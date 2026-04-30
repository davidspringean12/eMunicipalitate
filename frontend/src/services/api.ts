import axios from 'axios';

const api = axios.create({
  baseURL: '/api',
});

// Attach JWT token to every request if present
api.interceptors.request.use((config) => {
  const token = localStorage.getItem('accessToken');
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
    // Include user ID from token claims (simplified; in production decode JWT)
    const userId = localStorage.getItem('userId');
    if (userId) {
      config.headers['X-User-Id'] = userId;
    }
  }
  return config;
});

// Auth API
export const authApi = {
  getChallenge: () => api.get('/auth/challenge'),
  verify: (data: { sessionId: string; signedNonce: string; authCertificate: string }) =>
    api.post('/auth/verify', data),
  devToken: (role: string = 'CITIZEN') =>
    api.post(`/auth/dev-token?role=${role}`),
};

/** Decode JWT payload to extract claims (no verification — dev only) */
export function decodeJwtPayload(token: string): Record<string, unknown> {
  const payload = token.split('.')[1];
  return JSON.parse(atob(payload));
}

// Service Requests API
export const requestsApi = {
  create: (data: { serviceType: string; formData: Record<string, unknown> }) =>
    api.post('/requests', data),
  submit: (id: string) => api.post(`/requests/${id}/submit`),
  findMy: (page = 0) => api.get('/requests/my', { params: { page, size: 20 } }),
  findById: (id: string) => api.get(`/requests/${id}`),
  findPending: (page = 0) => api.get('/requests/pending', { params: { page, size: 20 } }),
  updateStatus: (id: string, status: string, rejectionReason?: string) =>
    api.patch(`/requests/${id}/status`, null, { params: { status, rejectionReason } }),
};

// Documents API
export const documentsApi = {
  upload: (requestId: string, file: File) => {
    const formData = new FormData();
    formData.append('file', file);
    formData.append('requestId', requestId);
    return api.post('/documents/upload', formData);
  },
  findByRequest: (requestId: string) => api.get(`/documents/request/${requestId}`),
  download: (id: string) => api.get(`/documents/${id}/download`, { responseType: 'blob' }),
};

// Signing API
export const signingApi = {
  initiate: (documentId: string) => api.post('/sign/initiate', { documentId }),
  complete: (data: { signSessionId: string; signatureValue: string; qesCertificate: string }) =>
    api.post('/sign/complete', data),
  devSign: (documentId: string) => api.post('/sign/dev-sign', { documentId }),
};

export default api;
