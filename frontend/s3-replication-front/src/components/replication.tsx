import React, {useEffect, useState} from "react";
import replicationService from "../service/replication.service";
import ReplicationSettings from "../model/ReplicationSettings";

const Replication: React.FC = () => {
    const [settings, setSettings] = useState<ReplicationSettings>()
    const [enabled, setEnabled] = useState<boolean>()
    useEffect(() => {
        replicationService.getSettings().then(
            (response) => {
                setSettings(response.data);
                setEnabled(response.data?.enabled ?? false)
                console.log("WTF")
            },
            (error) => {
                const _content = error?.response?.data?.message || error.message || error.toString();
                setSettings(_content);
                console.log("WTF X2")
            }
        );
    }, []);

    const changeReplicationState = () => {
        if (enabled) {
            replicationService.disable().then(
                (response) => {
                    setSettings(response.data);
                    setEnabled(response.data?.enabled ?? false)
                    console.log("WTF")
                },
                (error) => {
                    const _content = error?.response?.data?.message || error.message || error.toString();
                    setSettings(_content);
                    console.log("WTF X2")
                }
            );
        } else {
            replicationService.enable().then(
                (response) => {
                    setSettings(response.data);
                    setEnabled(response.data?.enabled ?? false)
                    console.log("WTF")
                },
                (error) => {
                    const _content = error?.response?.data?.message || error.message || error.toString();
                    setSettings(_content);
                    console.log("WTF X2")
                }
            );
        }
    }

    return (
        <div>
            <h2>Replication settings:</h2>
            <div className="col-md-3">
                <ul className="list-group">
                    <li className="list-group-item">
                        <label className="toggle-btn">
                            <input type="checkbox" className="toggle-btn" defaultChecked={enabled}
                                   onClick={changeReplicationState}/>
                            <span className="toggle-btn"/>
                            <strong className="toggle-btn">{enabled ? "Enabled" : "Disabled"}</strong>
                        </label>
                    </li>
                    <li className="list-group-item">
                        <strong>Status:</strong>
                        {" "}
                        {settings?.status ?? "Unknown"}
                    </li>
                </ul>
            </div>
        </div>
    )
}
export default Replication;
