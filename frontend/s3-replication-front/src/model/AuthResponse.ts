interface AuthResponse {
    type: string | null;
    accessToken: string | null;
    refreshToken: string | null;
    error: string | null;

}

export default AuthResponse;
