enum FileStatus {
    EXISTS,
    REMOVED,
}

export default interface BucketAndStatus {
    bucketName: String,
    status: FileStatus,
    fileProperties: Map<String, String>
}

export default interface Status {
    fileName: string;
    bucketAndStatus: Array<BucketAndStatus>
}



