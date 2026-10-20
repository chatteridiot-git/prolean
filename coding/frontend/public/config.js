window.__PROLEAN_CONFIG__ =
  window.location.port === '5173' || window.location.port === '4173'
    ? {
        authApiUrl: 'http://localhost:8081',
        userApiUrl: 'http://localhost:8082',
        taskApiUrl: 'http://localhost:8083',
        timelineApiUrl: 'http://localhost:8084',
      }
    : {
        authApiUrl: '',
        userApiUrl: '',
        taskApiUrl: '',
        timelineApiUrl: '',
      };
