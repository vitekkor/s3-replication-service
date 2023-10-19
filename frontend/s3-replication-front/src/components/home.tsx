import React from "react";
import {Link} from "react-router-dom";

const Home: React.FC = () => {
    return (
        <div className="tiles-container">
            <Link to="/users" className="tile">
                <p>Управление пользователями</p>
            </Link>
            <Link to="/s3" className="tile">
                <p>Просмотр статуса файлов в s3</p>
            </Link>
            <Link to="/replication" className="tile">
                <p>Управление репликацией</p>
            </Link>
        </div>
    )
}

export default Home;
