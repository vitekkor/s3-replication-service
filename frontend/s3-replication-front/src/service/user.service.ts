import {instance} from "../auth/api.config";
import User from "../model/user";
import {AxiosResponse} from "axios";

class UserService {
    getUsers(): Promise<AxiosResponse<Array<User>>> {
        return instance.get<Array<User>>("user/")
    }

    save(user: User) {
        return instance.post("user/save", user)
    }

    create(user: User) {
        return instance.post("user/create", user)
    }
}

const userService = new UserService();
export default userService;
