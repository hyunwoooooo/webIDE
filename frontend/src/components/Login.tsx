import React, { useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../contexts/AuthContext';
import '../styles/Login.css';

const Login: React.FC = () => {
    const navigate = useNavigate();
    const { login } = useAuth();

    const handleGoogleLogin = async () => {
        try {
            const response = await fetch('http://localhost:8080/api/v1/oauth2/google', {
                method: 'POST',
            });
            const data = await response.json();
            window.location.href = data.url;
        } catch (error) {
            console.error('로그인 에러:', error);
        }
    };

    useEffect(() => {
        // URL에서 code 파라미터 확인
        const urlParams = new URLSearchParams(window.location.search);
        const code = urlParams.get('code');
        
        if (code) {
            // code가 있다면 백엔드로 전송하여 사용자 정보를 받아옴
            fetch(`http://localhost:8080/api/v1/oauth2/google?code=${code}`, {
                method: 'GET',
            })
            .then(response => response.json())
            .then(data => {
                // 로그인 성공 후 토큰 저장 및 메인 페이지로 이동
                login(data.user, {
                    accessToken: data.accessToken,
                    refreshToken: data.refreshToken,
                    expiresIn: data.expiresIn
                });
                
                // URL 파라미터를 제거하고 메인 페이지로 리다이렉트
                window.history.replaceState({}, document.title, window.location.pathname);
                navigate('/');
            })
            .catch(error => {
                console.error('인증 에러:', error);
            });
        }
    }, [navigate, login]);

    return (
        <div className="login-container">
            <div className="login-box">
                <h1>WebIDE</h1>
                <p>코딩을 더 쉽고 효율적으로</p>
                <button className="google-login-button" onClick={handleGoogleLogin}>
                    <img 
                        src="https://www.google.com/favicon.ico" 
                        alt="Google" 
                        className="google-icon"
                    />
                    Google로 로그인
                </button>
            </div>
        </div>
    );
};

export default Login; 