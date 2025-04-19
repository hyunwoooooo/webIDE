import React, { createContext, useContext, useState, useEffect } from 'react';
import { User, AuthTokens, AuthState } from '../types/auth';

interface AuthContextType extends AuthState {
    login: (user: User, tokens: AuthTokens) => void;
    logout: () => void;
    refreshAccessToken: () => Promise<void>;
}

const AuthContext = createContext<AuthContextType | undefined>(undefined);

export const AuthProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
    const [authState, setAuthState] = useState<AuthState>({
        user: null,
        tokens: null,
        isAuthenticated: false
    });

    useEffect(() => {
        // localStorage에서 인증 상태 복원
        const storedUser = localStorage.getItem('user');
        const storedTokens = localStorage.getItem('tokens');
        
        if (storedUser && storedTokens) {
            setAuthState({
                user: JSON.parse(storedUser),
                tokens: JSON.parse(storedTokens),
                isAuthenticated: true
            });
        }
    }, []);

    const login = (user: User, tokens: AuthTokens) => {
        setAuthState({
            user,
            tokens,
            isAuthenticated: true
        });
        localStorage.setItem('user', JSON.stringify(user));
        localStorage.setItem('tokens', JSON.stringify(tokens));
    };

    const logout = () => {
        setAuthState({
            user: null,
            tokens: null,
            isAuthenticated: false
        });
        localStorage.removeItem('user');
        localStorage.removeItem('tokens');
    };

    const refreshAccessToken = async () => {
        if (!authState.tokens?.refreshToken) {
            logout();
            return;
        }

        try {
            const response = await fetch('http://localhost:8080/api/v1/oauth2/refresh', {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json',
                },
                body: JSON.stringify({
                    refreshToken: authState.tokens.refreshToken
                })
            });

            if (!response.ok) {
                throw new Error('토큰 갱신 실패');
            }

            const newTokens: AuthTokens = await response.json();
            setAuthState(prev => ({
                ...prev,
                tokens: newTokens
            }));
            localStorage.setItem('tokens', JSON.stringify(newTokens));
        } catch (error) {
            console.error('토큰 갱신 에러:', error);
            logout();
        }
    };

    const value = {
        ...authState,
        login,
        logout,
        refreshAccessToken
    };

    return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
};

export const useAuth = () => {
    const context = useContext(AuthContext);
    if (context === undefined) {
        throw new Error('useAuth must be used within an AuthProvider');
    }
    return context;
}; 