enum ReplicationStatus {
    DISABLED, ACTIVE, BACKGROUND_WORK
}

export default interface ReplicationSettings {
    id: string
    enabled: boolean
    status: ReplicationStatus,
}
