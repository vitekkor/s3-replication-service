import {instance} from "../auth/api.config";
import {AxiosResponse} from "axios";
import Status from "../model/fileStatus";

class FileService {
    getFiles(): Promise<AxiosResponse<Array<Status>>> {
        return instance.get<Array<Status>>("api/statuses")
    }
}

const fileService = new FileService();
export default fileService;
