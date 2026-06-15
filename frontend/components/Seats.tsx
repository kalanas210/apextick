'use client';

import { useSeats, useHoldSeat } from '@/hooks/useSeats';
import type { Seat } from '@/lib/types';

const statusStyles: Record<Seat['status'], string> = {
  AVAILABLE: 'bg-green-600 hover:bg-green-700 cursor-pointer',
  HELD: 'bg-yellow-600 cursor-not-allowed',
  BOOKED: 'bg-gray-600 cursor-not-allowed',
};

export default function Seats({ eventId }: { eventId: number }) {
  const { data: seats, isLoading, error } = useSeats(eventId);
  const holdSeat = useHoldSeat(eventId);

  if (isLoading) return <p>Loading seats…</p>;
  if (error) return <p>Failed to load seats.</p>;

  return (
    <div className="grid grid-cols-5 gap-2">
      {seats?.map((seat) => (
        <button
          key={seat.id}
          disabled={seat.status !== 'AVAILABLE' || holdSeat.isPending}
          onClick={() => holdSeat.mutate(seat.id)}
          className={`rounded p-3 text-sm font-medium text-white disabled:opacity-70 ${statusStyles[seat.status]}`}
        >
          {seat.seatNumber}
          <span className="block text-xs opacity-80">{seat.status}</span>
        </button>
      ))}
    </div>
  );
}