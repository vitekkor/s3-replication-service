export default interface User {
    login: string,
    password: string | null,
    roles: Array<string>,
    isActive: boolean,
    scopes: Array<string>,
    files: Array<string>,
    ips: Array<string>
}
