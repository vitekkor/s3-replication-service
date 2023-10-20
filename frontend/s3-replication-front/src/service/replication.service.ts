import {instance} from "../auth/api.config";
import {AxiosResponse} from "axios";
import ReplicationSettings from "../model/ReplicationSettings";

class ReplicationService {
    getSettings(): Promise<AxiosResponse<ReplicationSettings>> {
        return instance.get<ReplicationSettings>("replication/settings")
    }

    enable() {
        return instance.get("replication/enable")
    }

    disable() {
        return instance.get("replication/disable")
    }
}

const replicationService = new ReplicationService();
export default replicationService;
