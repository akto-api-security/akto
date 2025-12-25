import React, { useState } from 'react'
import { AttachmentMajor, ArrowUpMinor, CancelSmallMinor } from '@shopify/polaris-icons'
import './AskAktoChatInput.css'

function AskAktoChatInput({ 
    value, 
    onChange, 
    onSubmit, 
    placeholder = "How can I help you today?",
    disabled = false 
}) {
    const [attachedFiles, setAttachedFiles] = useState([])

    const handleAttachmentClick = () => {
        const input = document.createElement('input')
        input.type = 'file'
        input.accept = '.pdf,.doc,.docx,.txt,.png,.jpg,.jpeg,.gif,.bmp,.webp'
        input.multiple = true
        input.onchange = (e) => {
            const files = Array.from(e.target.files)
            files.forEach(file => {
                if (file) {
                    const fileObj = {
                        id: Date.now() + Math.random(),
                        file: file,
                        name: file.name,
                        size: file.size,
                        type: file.type,
                        preview: null
                    }
                    
                    // Create preview for images
                    if (file.type.startsWith('image/')) {
                        const reader = new FileReader()
                        reader.onload = (e) => {
                            fileObj.preview = e.target.result
                            setAttachedFiles(prev => [...prev, fileObj])
                        }
                        reader.readAsDataURL(file)
                    } else {
                        setAttachedFiles(prev => [...prev, fileObj])
                    }
                }
            })
        }
        input.click()
    }

    const handleRemoveFile = (fileId) => {
        setAttachedFiles(prev => prev.filter(file => file.id !== fileId))
    }

    const handleKeyPress = (e) => {
        if (e.key === 'Enter' && !e.shiftKey) {
            e.preventDefault()
            handleSubmit()
        }
    }

    const handleSubmit = () => {
        if (!value.trim() && attachedFiles.length === 0) return
        onSubmit(value, attachedFiles)
        setAttachedFiles([]) // Clear attachments after submit
    }

    return (
        <div className="akto-chat-input-main">
            <div className="akto-chat-input-wrapper">
                {/* File attachments display inside chat input */}
                {attachedFiles.length > 0 && (
                    <div className="akto-attached-files-inline">
                        {attachedFiles.map((file) => (
                            <div key={file.id} className="akto-attached-file-inline-item">
                                {file.preview ? (
                                    <div 
                                        className="akto-attached-file-inline-thumbnail"
                                        style={{
                                            backgroundImage: `url(${file.preview})`
                                        }}
                                        title={file.name}
                                    />
                                ) : (
                                    <div className="akto-attached-file-inline-icon">
                                        📄
                                    </div>
                                )}
                                <button 
                                    className="akto-remove-file-inline-button"
                                    onClick={() => handleRemoveFile(file.id)}
                                    title="Remove file"
                                >
                                    <CancelSmallMinor className="akto-remove-file-inline-icon" />
                                </button>
                            </div>
                        ))}
                    </div>
                )}
                <input
                    type="text"
                    className="akto-main-chat-input"
                    value={value}
                    onChange={onChange}
                    onKeyPress={handleKeyPress}
                    placeholder={placeholder}
                    disabled={disabled}
                />
            </div>
            <button 
                className="akto-attachment-button"
                onClick={handleAttachmentClick}
                title="Attach file"
            >
                <AttachmentMajor className="akto-attachment-icon" />
            </button>
            <button
                className="akto-send-button"
                onClick={handleSubmit}
                disabled={(!value.trim() && attachedFiles.length === 0) || disabled}
                title="Send message"
            >
                <ArrowUpMinor className="akto-send-icon" />
            </button>
        </div>
    )
}

export default AskAktoChatInput