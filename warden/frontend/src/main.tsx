import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';

import '@fontsource-variable/inter/index.css'; // self-hosted: no request to an outside font CDN

import { App } from './App';
import './index.css';

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <App />
  </StrictMode>,
);
