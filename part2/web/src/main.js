import { createClient } from '@supabase/supabase-js';
import { initWeb, isHttpsUrl } from './app.js';

const config = Object.freeze({
  supabaseUrl: import.meta.env.VITE_SUPABASE_URL || '',
  supabasePublishableKey: import.meta.env.VITE_SUPABASE_PUBLISHABLE_KEY || '',
  chatServiceBaseUrl: import.meta.env.VITE_CHAT_SERVICE_BASE_URL || '',
});
const configIsUsable = Boolean(config.supabasePublishableKey
  && isHttpsUrl(config.supabaseUrl)
  && isHttpsUrl(config.chatServiceBaseUrl));

const auth = configIsUsable
  ? createClient(config.supabaseUrl, config.supabasePublishableKey, {
      auth: {
        persistSession: false,
        autoRefreshToken: false,
        detectSessionInUrl: false,
        flowType: 'pkce',
      },
    }).auth
  : null;

initWeb({ auth, config });
