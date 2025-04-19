interface Window {
    gapi: {
        auth2: {
            getAuthInstance(): {
                signOut(): Promise<void>;
            };
        };
    };
} 