import { useEffect, useRef } from 'react';
import SockJS from 'sockjs-client';
import { Client } from '@stomp/stompjs';
import { WebSocketMessage } from '../types';

interface WebSocketHookProps {
  sessionId: string;
  onOutput: (output: string) => void;
  onDebugStatus: (status: string) => void;
  onError: (error: string) => void;
  onShowError: () => void;
}

export const useWebSocket = ({
  sessionId,
  onOutput,
  onDebugStatus,
  onError,
  onShowError,
}: WebSocketHookProps) => {
  const stompClient = useRef<Client | null>(null);

  useEffect(() => {
    const socket = new SockJS('http://localhost:8080/ws');
    const client = new Client({
      webSocketFactory: () => socket,
      onConnect: () => {
        console.log('WebSocket 연결됨');
        
        client.subscribe(`/topic/output/${sessionId}`, (message: WebSocketMessage) => {
          onOutput(message.body);
        });

        client.subscribe(`/topic/debug/${sessionId}`, (message: WebSocketMessage) => {
          onDebugStatus(message.body);
        });
        
        client.subscribe(`/topic/error/${sessionId}`, (message: WebSocketMessage) => {
          try {
            const errorData = JSON.parse(message.body);
            if (errorData.error === '컴파일 오류' && errorData.details) {
              const errorDetails = errorData.details.map((detail: any) => {
                return `라인 ${detail.line}${detail.column ? `, 열 ${detail.column}` : ''}: ${detail.message}`;
              }).join('\n');
              onError(`컴파일 오류:\n${errorDetails}`);
              onShowError();
            } else {
              onError(errorData.error || errorData.message || message.body);
              onShowError();
            }
          } catch (e) {
            onError(message.body);
            onShowError();
          }
        });
      }
    });

    client.activate();
    stompClient.current = client;

    return () => {
      if (stompClient.current) {
        stompClient.current.deactivate();
      }
    };
  }, [sessionId, onOutput, onDebugStatus, onError, onShowError]);

  return stompClient;
}; 