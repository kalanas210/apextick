import axios from 'axios';

// This module can be evaluated during server-side rendering, where `window`
// doesn't exist — so guard it. Your API calls only ever fire in the browser,
// where window IS defined and gives the correct host.
const baseURL =
    typeof window !== 'undefined'
        ? `http://${window.location.hostname}:8081`
        : '';

export const api = axios.create({ baseURL });