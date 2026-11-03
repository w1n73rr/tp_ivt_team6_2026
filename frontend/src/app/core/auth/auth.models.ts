export interface LoginRequest {
  username: string;
  password: string;
}

export interface RegisterRequest {
  username: string;
  email: string;
  password: string;
}

// Реальный ответ backend
export interface JwtResponse {
  token: string;
}

// Результат для компонентов интерфейса
export interface AuthResponse {
  success: boolean;
  message: string;
}