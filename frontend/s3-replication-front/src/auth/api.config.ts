import axios from "axios";
import {useNavigate} from "react-router-dom";

const baseurl: string = process.env.REACT_APP_SERVER_BASE_URL as string
export const instance = axios.create({
    //withCredentials: true,
    baseURL: baseurl,
});

instance.interceptors.request.use(
    (config) => {
        config.headers.Authorization = `Bearer ${localStorage.getItem("token")}`
        return config
    }
)

instance.interceptors.response.use(
    (config) => {
        return config;
    },
    async (error) => {
        const originalRequest = {...error.config};
        originalRequest._isRetry = true;
        if (
            error.response?.status === 401 &&
            error.config &&
            !error.config._isRetry
        ) {
            try {
                // запрос на обновление токенов
                const resp = await instance.post("auth/refresh", {'refreshToken': localStorage.getItem("refreshToken")});
                // сохраняем новый accessToken в localStorage

                localStorage.setItem("token", resp.data.accessToken!);
                localStorage.setItem("refreshToken", resp.data.refreshToken!);
                // переотправляем запрос с обновленным accessToken
                return instance.request(originalRequest);
            } catch (error) {
                console.log("AUTH ERROR");
            }
        }
        if (error.config._isRetry) {
            useNavigate()('/login')
            return
        }
        // на случай, если возникла другая ошибка (не связанная с авторизацией)
        // пробросим эту ошибку
        throw error;
    }
);
