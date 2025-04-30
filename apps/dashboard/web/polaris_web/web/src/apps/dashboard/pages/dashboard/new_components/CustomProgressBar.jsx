import React from 'react';

const CustomProgressBar = ({ progress, topColor, backgroundColor, borderRadius, height }) => {
  const containerStyle = {
    width: '100%',
    backgroundColor: backgroundColor || '#e0e0e0',
    borderRadius: borderRadius || '5px',
    height: height || '16px',
    overflow: 'hidden',
  };

  const fillerStyle = {
    height: '100%',
    width: `${progress}%`,
    backgroundColor: topColor || '#3b82f6',
    borderRadius: 'inherit',
    transition: 'width 0.3s ease-in-out',
  };

  return (
    <div style={containerStyle}>
      <div style={fillerStyle}></div>
    </div>
  );
};

export default CustomProgressBar;
