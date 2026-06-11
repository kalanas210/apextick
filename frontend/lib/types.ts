export type SeatStatus = 'AVAILABLE' | 'HELD' | 'BOOKED';

export interface Seat {
    id: number;
    seatNumber: string;
    status: SeatStatus;
}