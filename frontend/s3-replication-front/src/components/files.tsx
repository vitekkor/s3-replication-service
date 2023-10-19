import React, {ChangeEvent, useCallback, useEffect, useState} from "react";

import fileService from "../service/file.service"
import Status from "../model/fileStatus";

const FileList: React.FC = () => {
    const [allFiles, setAllFiles] = useState<Array<Status>>([]);
    const [content, setContent] = useState<Array<Status>>([]);
    const [currentIndex, setCurrentIndex] = useState<number>(-1);
    const [searchFileName, setSearchFileName] = useState<string>("");


    const findByFileName = () => {
        setContent(allFiles.filter((status) => status.fileName.startsWith(searchFileName)))
        setCurrentIndex(-1)
    };

    const onChangeSearchTitle = (e: ChangeEvent<HTMLInputElement>) => {
        const searchFileNameValue = e.target.value;
        if (searchFileNameValue === '') {
            setContent(allFiles);
        }
        setSearchFileName(searchFileNameValue);
        if (searchFileNameValue !== '') {
            findByFileName();
        }
    };

    const escFunction = useCallback((event: KeyboardEvent) => {
        if (event.key === "Escape") {
            setCurrentIndex(-1)
        }
    }, []);

    useEffect(() => {
        fileService.getFiles().then(
            (response) => {
                setAllFiles(response.data);
                setContent(response.data);
            },
            (error) => {
                const _content = error?.response?.data?.message || error.message || error.toString();
                setContent(_content);
            }
        );
        document.addEventListener("keydown", escFunction, false);

        return () => {
            document.removeEventListener("keydown", escFunction, false);
        };
    }, [escFunction]);

    // @ts-ignore
    return (
        <div className="list row">
            <div className="col-md-8">
                <div className="input-group mb-3">
                    <input
                        type="text"
                        className="form-control"
                        placeholder="Search by filename"
                        value={searchFileName}
                        onChange={onChangeSearchTitle}
                    />
                    <div className="input-group-append">
                        <button
                            className="btn btn-outline-secondary"
                            type="button"
                            onClick={findByFileName}
                        >
                            Search
                        </button>
                    </div>
                </div>
            </div>
            <div className="col-md-auto">
                <ul className="list-group">
                    {Array.from(content).map((status, index) => (
                        <li
                            className={
                                "list-group-item " + (index === currentIndex ? "active" : "")
                            }
                            key={index}
                        >
                            <div className="row">
                                <div className="column col-md-auto">
                                    <strong>FileName:</strong>
                                    {" "}
                                    {status.fileName}
                                </div>
                                <div className="column col-md-auto">
                                    <strong>Buckets:</strong>
                                    {" "}
                                    <ul className="list-group">
                                        {Array.from(status.bucketAndStatus).map((bucket, index) => (
                                            <li className="list-group-item" key={index}>
                                                <div className="row">
                                                    <div className="col-md-auto">
                                                        <strong>Bucket:</strong>
                                                        {" "}
                                                        {bucket.bucketName}
                                                    </div>
                                                    <div className="col-md-auto">
                                                        <strong>Status:</strong>
                                                        {bucket.status}
                                                    </div>
                                                    <div className="col-md-auto">
                                                        <strong>Properties:</strong>
                                                        {" "}
                                                        <ul className="list-group">
                                                        {// @ts-ignore
                                                            Object.keys(bucket.fileProperties).map((key) => `${key}: ${bucket.fileProperties[key]}`).map((prop, index) => (
                                                                <li className="list-group-item" key={index}>
                                                                    {prop}
                                                                </li>
                                                            ))}
                                                        </ul>
                                                    </div>
                                                </div>
                                            </li>
                                        ))
                                        }
                                    </ul>

                                </div>
                            </div>

                        </li>
                    ))}
                </ul>

            </div>

        </div>
    );
};

export default FileList;
