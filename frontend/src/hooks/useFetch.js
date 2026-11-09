import { useEffect, useState } from 'react';
import api, { errorMessage } from '../api/client.js';

/** GETs a URL whenever it or its params change. Returns { data, error, loading }. */
export default function useFetch(url, params) {
  const [state, setState] = useState({ data: null, error: '', loading: true });
  const key = JSON.stringify(params || {});

  useEffect(() => {
    let cancelled = false;
    setState({ data: null, error: '', loading: true });
    api.get(url, { params })
      .then(({ data }) => { if (!cancelled) setState({ data, error: '', loading: false }); })
      .catch((e) => { if (!cancelled) setState({ data: null, error: errorMessage(e), loading: false }); });
    return () => { cancelled = true; };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [url, key]);

  return state;
}
