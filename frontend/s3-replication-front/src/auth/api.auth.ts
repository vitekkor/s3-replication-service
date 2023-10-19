import {instance} from "./api.config";
import AuthResponse from "../model/AuthResponse";
import {AxiosResponse} from "axios";

class AuthService {

    login(login: string, password: string) {
        return instance.post<AuthResponse>("auth/login", {"login": login, "password": password});
    }

    refreshToken() {
        return instance.get("auth/refresh");
    }

    logout() {
        return instance.post("auth/logout")
    }

    setAuthToken(authResponse: AxiosResponse<AuthResponse>) {
        if (!this.checkAdminRights(authResponse)) {
            throw Error("Access denied")
        }
        localStorage.setItem("token", authResponse.data.accessToken!);
        localStorage.setItem("refreshToken", authResponse.data.refreshToken!);
    }

    haveUser() {
        let token = localStorage.getItem("token")
        let refreshToken = localStorage.getItem("refreshToken")
        if (token === null || refreshToken === null) {
            return Promise.resolve({"data": false});
        }
        return instance.post<boolean>("auth/validate", {"token": token, "type": "ACCESS"})
    }

    checkAdminRights(authResponse: AxiosResponse<AuthResponse>) {
        let base64Url = authResponse.data.accessToken!.split('.')[1];
        let base64 = base64Url.replace(/-/g, '+').replace(/_/g, '/');
        let jsonPayload = decodeURIComponent(window.atob(base64).split('').map(function (c) {
            return '%' + ('00' + c.charCodeAt(0).toString(16)).slice(-2);
        }).join(''));

        let jwt = JSON.parse(jsonPayload);
        return jwt['roles']?.split(',')?.includes("ROLE_ADMIN") || false
    }
}

const authService = new AuthService();
export default authService;
