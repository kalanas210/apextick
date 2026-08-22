'use client';

import { useEffect, useRef } from 'react';
import { Client, type IMessage } from '@stomp/stompjs';
import type { SeatStatus } from './types';

export interface SeatStatusChange {
    seatId: number;
    status: SeatStatus;
    heldUntil: string | null;
}

interface SeatStatusMessage {
    type: string;
    eventId: number;
    occurredAt: string;
    seats: SeatStatusChange[];
}

function websocketUrl(): string {
    const { protocol, hostname, origin } = window.location;
    // Behind Caddy the API is same-origin; locally it answers on :8081.
    const httpBase = protocol === 'https:' ? origin : `http://${hostname}:8081`;
    return `${httpBase.replace(/^http/, 'ws')}/api/ws`;
}

/**
 * Subscribes to live seat changes for one event over STOMP.
 *
 * The booking service fans these out through Redis, so every instance
 * broadcasts the same flips and the map stays honest with several servers
 * running. The handshake is public; only holding a seat needs a token.
 */
export function useSeatUpdates(
    eventId: number | undefined,
    onChange: (changes: SeatStatusChange[]) => void,
) {
    // Keep the latest callback without re-subscribing on every render.
    const handler = useRef(onChange);
    useEffect(() => {
        handler.current = onChange;
    }, [onChange]);

    useEffect(() => {
        if (!eventId) return;

        const client = new Client({
            brokerURL: websocketUrl(),
            reconnectDelay: 4000,
            heartbeatIncoming: 10000,
            heartbeatOutgoing: 10000,
            onConnect: () => {
                client.subscribe(`/topic/events/${eventId}/seats`, (message: IMessage) => {
                    try {
                        const body = JSON.parse(message.body) as SeatStatusMessage;
                        if (body.seats?.length) {
                            handler.current(body.seats);
                        }
                    } catch {
                        // a malformed frame should never take the seat map down
                    }
                });
            },
        });

        client.activate();
        return () => {
            void client.deactivate();
        };
    }, [eventId]);
}
