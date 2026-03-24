import { createTheme } from '@mui/material/styles';

export const theme = createTheme({
  palette: {
    mode: 'dark',
    primary: { main: '#2E75B6', light: '#5CA0E0', dark: '#1B5A94' },
    secondary: { main: '#FF9800' },
    error: { main: '#E53935' },
    warning: { main: '#FF9800' },
    success: { main: '#43A047' },
    info: { main: '#1E88E5' },
    background: {
      default: '#0A1118',
      paper: '#111D29',
    },
    text: {
      primary: '#E0E6ED',
      secondary: '#7A8FA3',
    },
  },
  typography: {
    fontFamily: '"Noto Sans KR", "Roboto", sans-serif',
    h5: { fontWeight: 700, letterSpacing: '-0.5px' },
    h6: { fontWeight: 600, letterSpacing: '-0.3px' },
    subtitle1: { fontWeight: 500 },
    body2: { color: '#7A8FA3' },
  },
  shape: { borderRadius: 12 },
  components: {
    MuiPaper: {
      styleOverrides: {
        root: {
          backgroundImage: 'none',
          border: '1px solid #1E3044',
        },
      },
    },
    MuiCard: {
      styleOverrides: {
        root: {
          backgroundImage: 'none',
          border: '1px solid #1E3044',
          boxShadow: '0 4px 20px rgba(0,0,0,0.3)',
        },
      },
    },
  },
});

// 위험도 색상 매핑
export const riskColors = {
  safe: '#43A047',
  caution: '#1E88E5',
  warning: '#FF9800',
  danger: '#E53935',
} as const;

export const statusColors = {
  good: '#66BB6A',
  normal: '#42A5F5',
  bad: '#FFB74D',
  veryBad: '#EF5350',
} as const;
