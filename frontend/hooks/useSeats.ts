'use client';

import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { useAuth } from 'react-oidc-context';
import { api } from '@/lib/api';
import type { Seat } from '@/lib/types';

export function useSeats(eventId: number) {
  const auth = useAuth();
  const token = auth.user?.access_token;

  return useQuery({
    queryKey: ['seats', eventId],
    enabled: !!token,
    queryFn: async () => {
      const res = await api.get<Seat[]>(`/api/events/${eventId}/seats`, {
        headers: { Authorization: `Bearer ${token}` },
      });
      return res.data;
    },
  });
}

export function useHoldSeat(eventId: number) {
  const auth = useAuth();
  const token = auth.user?.access_token;
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: async (seatId: number) => {
      const res = await api.post(
        `/api/seats/${seatId}/hold`,
        {},
        { headers: { Authorization: `Bearer ${token}` } },
      );
      return res.data;
    },
    onSettled: () => {
      queryClient.invalidateQueries({ queryKey: ['seats', eventId] });
    },
  });
}