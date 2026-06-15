import axios from 'axios';

// This module can be evaluated during server-side rendering, where `window`
// doesn't exist — so guard it. Your API calls only ever fire in the browser,
// where window IS defined and gives the correct host.
//
// Over HTTPS we're behind the Caddy reverse proxy: the booking API is same-origin
// under /api (baseURL = origin). Over plain HTTP (local dev / direct-IP) we hit the
// booking service on its own port. Endpoints are prefixed with /api either way.
const baseURL =
    typeof window !== 'undefined'
        ? (window.location.protocol === 'https:'
            ? window.location.origin
            : `http://${window.location.hostname}:8081`)
        : '';

export const api = axios.create({ baseURL });