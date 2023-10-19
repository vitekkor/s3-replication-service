import React, {ChangeEvent, useCallback, useEffect, useState} from "react";
import Select, {MultiValue, SingleValue} from "react-select";

import userService from "../service/user.service"
import User from "../model/user";
import CreatableSelect from "react-select/creatable";

const getNewUser = () => {
    return new class implements User {
        files = [];
        ips = [];
        isActive = true;
        login = "";
        password = "";
        roles = [];
        scopes = [];
    }()
}

// @ts-ignore
const Modal = ({onRequestClose, user, createNew}) => {
    const [message, setMessage] = useState<string>("");
    const [newLogin, setNewLogin] = useState<string>("");
    const [newPassword, setNewPassword] = useState<string>("");

    const allowedScopes = [
        {value: "read", label: "READ"},
        {value: "write", label: "WRITE"}
    ];
    let userScopes = Array.from(user.scopes).map(scope => allowedScopes.find(allowedScope => allowedScope.value === scope)!)
    const [scopes, setScopes] = useState<Array<{ value: string, label: string }>>(userScopes);

    const allowedRoles = [
        {value: "ADMIN", label: "ADMIN"},
        {value: "USER", label: "USER"}
    ];
    let userRoles = Array.from(user.roles).map(role => allowedRoles.find(allowedRole => allowedRole.value === role)!)
    const [roles, setRoles] = useState<Array<{ value: string, label: string }>>(userRoles);

    let userFiles: Array<{ value: string, label: string }> = Array.from(user.files).map(file => {
        return {value: file, label: file} as { value: string, label: string }
    })
    const [files, setFiles] = useState<Array<{ value: string, label: string }>>(userFiles);

    let userIps: Array<{ value: string, label: string }> = Array.from(user.ips).map(ip => {
        return {value: ip, label: ip} as { value: string, label: string }
    })
    const [ips, setIps] = useState<Array<{ value: string, label: string }>>(userIps);

    const allowedStatuses = [
        {value: true, label: "Active"},
        {value: false, label: "Disabled"}
    ]

    let userStatus = user.isActive ? "Active" : "Disabled"

    const [status, setStatus] = useState<{ value: boolean, label: string }>({value: user.isActive, label: userStatus})

    const escFunction = useCallback((event: KeyboardEvent) => {
        if (event.key === "Escape") {
            onRequestClose()
        }
    }, [onRequestClose]);

    useEffect(() => {

        // Prevent scolling
        document.body.style.overflow = "hidden";
        document.addEventListener("keydown", escFunction, false);

        // Clear things up when unmounting this component
        return () => {
            document.body.style.overflow = "visible";
            document.removeEventListener("keydown", escFunction, false);
        };
    }, [escFunction]);

    const handleScopesChange = (scopes: MultiValue<{ value: string, label: string }>) => {
        setScopes(Array.from(scopes) || []);
    };

    const handleRolesChange = (roles: MultiValue<{ value: string, label: string }>) => {
        setRoles(Array.from(roles) || []);
    };

    const handleIpsChange = (ips: MultiValue<{ value: string, label: string }>) => {
        setIps(Array.from(ips) || []);
    };

    const handleStatusChange = (status: SingleValue<{ value: boolean, label: string }>) => {
        setStatus({value: status!.value, label: status!.label})
    };

    const handleFilesChange = (files: MultiValue<{ value: string, label: string }>) => {
        setFiles(Array.from(files) || []);
    };

    const save = () => {
        let newUser = new class implements User {
            files = files.map(file => file.value);
            ips = ips.map(ip => ip.value);
            isActive = status.value;
            login = user.login;
            password = (createNew === true) ? newPassword : null;
            roles = roles.map(role => role.value);
            scopes = scopes.map(scope => scope.value);
        }()
        if (!createNew) {
            userService.save(newUser).then(r => {
                    onRequestClose(newUser)
                },
                (error) => {
                    const resMessage = error?.response?.data?.error || error.message || error.toString();

                    setMessage(resMessage);
                })
        } else {
            userService.create(newUser).then(r => {
                    onRequestClose(newUser)
                },
                (error) => {
                    const resMessage = error?.response?.data?.error || error.message || error.toString();

                    setMessage(resMessage);
                })
        }
    }

    const onChangeLogin = (e: ChangeEvent<HTMLInputElement>) => {
        const newLoginValue = e.target.value;
        if (newLoginValue === '') {
            alert("Must be not null");
            return;
        }
        setNewLogin(newLoginValue);
    };

    const onChangePassword = (e: ChangeEvent<HTMLInputElement>) => {
        const newLoginValue = e.target.value;
        if (newLoginValue === '') {
            alert("Must be not null");
            return;
        }
        setNewPassword(newLoginValue);
    };

    return (
        <div className="modal__backdrop">
            <div className="modal__container">
                <h3 className="modal__title">Edit user {user.login}</h3>
                <div>
                    <h4>User</h4>
                    <div>
                        <label>
                            <strong>Login:</strong>
                        </label>{" "}
                        {createNew === true ? (
                            <div className="input-group mb-3">
                                <input
                                    type="text"
                                    className="form-control"
                                    placeholder="Login"
                                    value={newLogin}
                                    onChange={onChangeLogin}
                                />
                            </div>
                        ) : (user.login)}
                    </div>
                    <div>
                        <label>
                            <strong>Password:</strong>
                        </label>{" "}
                        {createNew === true ? (
                            <div className="input-group mb-3">
                                <input
                                    type="text"
                                    className="form-control"
                                    placeholder="Password"
                                    value={newPassword}
                                    onChange={onChangePassword}
                                />
                            </div>
                        ) : ("***")}
                    </div>
                    <div>
                        <label>
                            <strong>Scopes:</strong>
                        </label>{" "}
                        <div>
                            <form>
                                <Select
                                    options={allowedScopes}
                                    onChange={handleScopesChange}
                                    value={scopes}
                                    isMulti
                                />
                            </form>
                        </div>
                    </div>
                    <div>
                        <label>
                            <strong>Roles:</strong>
                        </label>{" "}
                        <form>
                            <Select
                                options={allowedRoles}
                                onChange={handleRolesChange}
                                value={roles}
                                isMulti
                            />
                        </form>
                    </div>
                    <div>
                        <label>
                            <strong>Files:</strong>
                        </label>{" "}
                        <CreatableSelect
                            isClearable
                            options={files}
                            onChange={handleFilesChange}
                            value={files}
                            isMulti
                        />
                        {user.files.join(", ")}
                    </div>
                    <div>
                        <label>
                            <strong>Allowed ips:</strong>
                        </label>{" "}
                        <form>
                            <CreatableSelect
                                isClearable
                                options={ips}
                                onChange={handleIpsChange}
                                value={ips}
                                isMulti
                            />
                        </form>
                    </div>
                    <div>
                        <label>
                            <strong>Status:</strong>
                        </label>{" "}
                        <form>
                            <Select
                                options={allowedStatuses}
                                onChange={handleStatusChange}
                                value={status}
                            />
                        </form>
                    </div>
                    <br/>
                    <button type="button" onClick={save}>
                        Save
                    </button>
                    {message && (
                        <div className="form-group">
                            <div className="alert alert-danger" role="alert">
                                {message}
                            </div>
                        </div>
                    )}
                </div>
            </div>
        </div>
    )
        ;
};

const BoardUser: React.FC = () => {
    const [allUsers, setAllUsers] = useState<Array<User>>([]);
    const [content, setContent] = useState<Array<User>>([]);
    const [currentUser, setCurrentUser] = useState<User | null>(null);
    const [currentIndex, setCurrentIndex] = useState<number>(-1);
    const [searchLogin, setSearchLogin] = useState<string>("");
    const [isModalOpen, setModalIsOpen] = useState(false);
    const [isCreateUserModalOpen, setCreateUserModalOpen] = useState(false);

    const setActiveUser = (user: User, index: number) => {
        if (currentUser === user) {
            setCurrentUser(null);
            setCurrentIndex(-1);
            return
        }
        setCurrentUser(user);
        setCurrentIndex(index);
    };

    const findByLogin = () => {
        setContent(allUsers.filter((user) => user.login.startsWith(searchLogin)))
        setCurrentUser(null)
        setCurrentIndex(-1)
    };

    const onChangeSearchTitle = (e: ChangeEvent<HTMLInputElement>) => {
        const searchLoginValue = e.target.value;
        if (searchLoginValue === '') {
            setContent(allUsers);
        }
        setSearchLogin(searchLoginValue);
        if (searchLoginValue !== '') {
            findByLogin();
        }
    };

    const escFunction = useCallback((event: KeyboardEvent) => {
        if (event.key === "Escape") {
            setCurrentUser(null)
            setCurrentIndex(-1)
        }
    }, []);

    useEffect(() => {
        userService.getUsers().then(
            (response) => {
                setAllUsers(response.data);
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

    const toggleModal = () => {
        setModalIsOpen(!isModalOpen);
    };

    const addUser = () => {
        setCreateUserModalOpen(!isCreateUserModalOpen);
    };

    const onCloseModal = (newUser: User) => {
        setCurrentUser(newUser);
        allUsers[currentIndex] = newUser;
        toggleModal()
    };

    return (
        <div className="list row">
            {isModalOpen && <Modal onRequestClose={onCloseModal} user={currentUser} createNew={false}/>}
            {isCreateUserModalOpen && <Modal onRequestClose={onCloseModal} user={getNewUser()} createNew={true}/>}
            <div className="col-md-8">
                <div className="input-group mb-3">
                    <input
                        type="text"
                        className="form-control"
                        placeholder="Search by login"
                        value={searchLogin}
                        onChange={onChangeSearchTitle}
                    />
                    <div className="input-group-append">
                        <button
                            className="btn btn-outline-secondary"
                            type="button"
                            onClick={findByLogin}
                        >
                            Search
                        </button>
                    </div>
                </div>
            </div>
            <div className="col-md-6">
                <div className="row-cols-md-auto">
                    <h4>Users</h4>
                    <br/>
                    <button onClick={addUser} type="button">
                        Add
                    </button>
                </div>
                <ul className="list-group">
                    {Array.from(content).map((user, index) => (
                        <li
                            className={
                                "list-group-item " + (index === currentIndex ? "active" : "")
                            }
                            onClick={() => setActiveUser(user, index)}
                            key={index}
                        >
                            {user.login}
                        </li>
                    ))}
                </ul>
            </div>
            <div className="col-md-6">
                {currentUser ? (
                    <div>
                        <h4>User</h4>
                        <div>
                            <label>
                                <strong>Login:</strong>
                            </label>{" "}
                            {currentUser.login}
                        </div>
                        <div>
                            <label>
                                <strong>Scopes:</strong>
                            </label>{" "}
                            {currentUser.scopes.join(", ")}
                        </div>
                        <div>
                            <label>
                                <strong>Roles:</strong>
                            </label>{" "}
                            {currentUser.roles.join(", ")}
                        </div>
                        <div>
                            <label>
                                <strong>Files:</strong>
                            </label>{" "}
                            {currentUser.files.join(", ")}
                        </div>
                        <div>
                            <label>
                                <strong>Allowed ips:</strong>
                            </label>{" "}
                            {currentUser.ips.join(", ")}
                        </div>
                        <div>
                            <label>
                                <strong>Status:</strong>
                            </label>{" "}
                            {currentUser.isActive ? "Active" : "Disabled"}
                        </div>
                        <div>
                            <br/>
                            <button onClick={toggleModal} type="button">
                                Edit
                            </button>
                        </div>

                    </div>
                ) : (
                    <div>

                    </div>
                )}
            </div>
        </div>
    );
};

export default BoardUser;
