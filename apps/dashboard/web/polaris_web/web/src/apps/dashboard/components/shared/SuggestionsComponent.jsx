import React from 'react'
import './SuggestionsComponent.css'

function SuggestionsComponent({ suggestions, onSuggestionClick }) {
    return (
        <div className="ask-akto-suggestions">
            <div className="suggestions-container">
                {suggestions.map((suggestion, index) => (
                    <button
                        key={index}
                        className="suggestion-item"
                        onClick={() => onSuggestionClick(suggestion)}
                    >
                        <svg className="suggestion-icon" xmlns="http://www.w3.org/2000/svg" width="17" height="13" viewBox="0 0 17 13" fill="none">
                            <path d="M16.0672 8.56719L12.3172 12.3172C12.1999 12.4345 12.0409 12.5003 11.875 12.5003C11.7091 12.5003 11.5501 12.4345 11.4328 12.3172C11.3155 12.1999 11.2497 12.0409 11.2497 11.875C11.2497 11.7091 11.3155 11.5501 11.4328 11.4328L14.1164 8.75H8.125C5.97081 8.74773 3.90551 7.89097 2.38227 6.36773C0.85903 4.84449 0.00227486 2.77919 0 0.625C0 0.45924 0.065848 0.300269 0.183058 0.183058C0.300269 0.0658481 0.45924 0 0.625 0C0.79076 0 0.949731 0.0658481 1.06694 0.183058C1.18415 0.300269 1.25 0.45924 1.25 0.625C1.25207 2.44773 1.97706 4.19521 3.26592 5.48408C4.55479 6.77294 6.30227 7.49793 8.125 7.5H14.1164L11.4328 4.81719C11.3155 4.69991 11.2497 4.54085 11.2497 4.375C11.2497 4.20915 11.3155 4.05009 11.4328 3.93281C11.5501 3.81554 11.7091 3.74965 11.875 3.74965C12.0409 3.74965 12.1999 3.81554 12.3172 3.93281L16.0672 7.68281C16.1253 7.74086 16.1714 7.80979 16.2029 7.88566C16.2343 7.96154 16.2505 8.04287 16.2505 8.125C16.2505 8.20713 16.2343 8.28846 16.2029 8.36434C16.1714 8.44021 16.1253 8.50914 16.0672 8.56719Z" fill="#898C90"/>
                        </svg>
                        <span className="suggestion-text">{suggestion}</span>
                    </button>
                ))}
            </div>
        </div>
    )
}

export default SuggestionsComponent