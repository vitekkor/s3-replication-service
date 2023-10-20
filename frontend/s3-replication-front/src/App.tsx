import React, {useEffect, useState} from "react";
import {Link, Route, Routes, useNavigate} from "react-router-dom";
import "bootstrap/dist/css/bootstrap.min.css";
import "./App.css";

import Login from "./components/login";
import authService from "./auth/api.auth";
import BoardUser from "./components/users";
import Home from "./components/home";
import FileList from "./components/files";
import Replication from "./components/replication";

const App: React.FC = () => {
    const navigate = useNavigate()
    const [user, setUser] = useState<boolean>(false)

    useEffect(() => {
        authService.haveUser().then(r => {
            setUser(r.data)
            if (!r.data) {
                navigate('/login')
            }
        }, (error) => {
            navigate('/login')
        })
    }, [navigate]);

    const logout = () => {
        setUser(false);
        authService.logout();
        navigate('/login');
    }

    return (
        <div>
            <nav className="navbar navbar-expand navbar-dark bg-dark">
                <Link to={"/"} className="navbar-brand">
                    S3 Replication App Admin
                </Link>
                <button
                    style={{marginLeft: "auto", fontSize: 14, marginBottom: 0}}
                    hidden={!user}
                    onClick={logout}
                >Logout
                </button>
            </nav>

            <div className="container mt-3">
                <Routes>
                    <Route path="/" element={<Home/>}/>
                    <Route path="/users" element={<BoardUser/>}/>
                    <Route path="/login" element={<Login/>}/>
                    <Route path="/s3" element={<FileList/>}/>
                    <Route path="/replication" element={<Replication/>}/>
                </Routes>
            </div>
        </div>
    );
};

export default App;
