import axios from "axios";

const BASE_URL = import.meta.env.VITE_API_BASE_URL || "http://localhost:8000";

const api = axios.create({
  baseURL: `${BASE_URL}/api`,
  headers: { "Content-Type": "application/json" },
});

const isAuthEndpoint = (url = "") =>
  url.includes("/auth/login") || url.includes("/auth/refresh");

// Attach JWT to protected requests only. Do not attach stale tokens to login.
api.interceptors.request.use((config) => {
  if (!isAuthEndpoint(config.url)) {
    const token = localStorage.getItem("access_token");
    if (token) {
      config.headers.Authorization = `Bearer ${token}`;
    }
  }
  return config;
});

// Auto-refresh protected API calls on 401. Never refresh/retry the login request.
api.interceptors.response.use(
  (res) => res,
  async (error) => {
    const original = error.config || {};

    if (
      error.response?.status === 401 &&
      !original._retry &&
      !isAuthEndpoint(original.url)
    ) {
      original._retry = true;
      const refresh = localStorage.getItem("refresh_token");
      if (refresh) {
        try {
          const { data } = await axios.post(`${BASE_URL}/api/auth/refresh`, { refresh });
          localStorage.setItem("access_token", data.access);
          original.headers = original.headers || {};
          original.headers.Authorization = `Bearer ${data.access}`;
          return api(original);
        } catch {
          localStorage.removeItem("access_token");
          localStorage.removeItem("refresh_token");
          window.location.href = "/login";
        }
      }
    }

    return Promise.reject(error);
  }
);

export default api;

// Auth
export const login = (email, password) => {
  localStorage.removeItem("access_token");
  localStorage.removeItem("refresh_token");
  return api.post("/auth/login", { email: email.trim().toLowerCase(), password: password.trim() });
};

const toRecordParams = (params = {}) => ({
  reviewStatus: params.review_status ?? params.reviewStatus,
  scopeCategory: params.scope_category ?? params.scopeCategory,
  sourceType: params.source_type ?? params.sourceType,
  suspiciousFlag: params.suspicious_flag ?? params.suspiciousFlag,
  batch: params.batch,
  search: params.search,
  page: params.page,
  pageSize: params.pageSize ?? params.page_size,
  ordering: params.ordering,
});

export const getMe = () => api.get("/auth/me");

// Dashboard
export const getDashboardStats = () => api.get("/dashboard/stats");

// Data sources
export const getDataSources = () => api.get("/data-sources");
export const createDataSource = (data) => api.post("/data-sources", data);

// Batches
export const getBatches = (params) => api.get("/batches", { params });
export const getBatch = (id) => api.get(`/batches/${id}`);

// Upload
export const uploadSAP = (formData) =>
  api.post("/upload/sap", formData, { headers: { "Content-Type": "multipart/form-data" } });

export const uploadUtility = (formData) =>
  api.post("/upload/utility", formData, { headers: { "Content-Type": "multipart/form-data" } });

export const uploadTravel = (formData) =>
  api.post("/upload/travel", formData, { headers: { "Content-Type": "multipart/form-data" } });

// Records
export const getRecords = (params) => api.get("/records", { params: toRecordParams(params) });
export const getRecord = (id) => api.get(`/records/${id}`);

// Reviews
export const reviewRecord = (id, action, comment) =>
  api.post(`/records/${id}/review`, { action, comment });

export const bulkReview = (record_ids, action, comment) =>
  api.post("/records/bulk-review", { recordIds: record_ids, action, comment });

// Audit
export const getRecordAudit = (id) => api.get(`/records/${id}/audit`);
export const getAuditLog = () => api.get("/audit");
