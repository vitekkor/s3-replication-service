import React, {useEffect} from "react";
import {Link, Route, Routes, useNavigate} from "react-router-dom";
import "bootstrap/dist/css/bootstrap.min.css";
import "./App.css";

import Login from "./components/login";
import authService from "./auth/api.auth";
import BoardUser from "./components/users";
import Home from "./components/home";
import FileList from "./components/files";

const App: React.FC = () => {
    const navigate = useNavigate()

    useEffect(() => {
        authService.haveUser().then(r => {
            if (!r.data) navigate('/login')
        })
    }, [navigate]);

    return (
        <div>
            <nav className="navbar navbar-expand navbar-dark bg-dark">
                <Link to={"/"} className="navbar-brand">
                    S3 Replication App Admin
                </Link>
            </nav>

            <div className="container mt-3">
                <Routes>
                    <Route path="/" element={<Home/>}/>
                    <Route path="/users" element={<BoardUser/>}/>
                    <Route path="/login" element={<Login/>}/>
                    <Route path="/s3" element={<FileList/>}/>
                </Routes>
            </div>
        </div>
    );
};

export default App;
