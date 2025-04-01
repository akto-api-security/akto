import React from 'react';

export const AgentImage = ({ src, alt }: { src: string, alt: string }) => {
    return (
        <div className="w-10 h-10 rounded-sm overflow-hidden relative border border-[#C9CCCF] flex-shrink-0">
            <img src={src} alt={alt} className="w-full h-full object-cover" />
        </div>
    )
}