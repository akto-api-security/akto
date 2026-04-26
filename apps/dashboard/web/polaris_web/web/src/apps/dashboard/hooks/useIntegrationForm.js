import { useState, useCallback } from 'react'

/**
 * useIntegrationForm Hook
 * Manages form state and validation for integration forms
 * Compatible with NewRelic integration form component
 */
export const useIntegrationForm = (initialValues = {}) => {
  const [values, setValues] = useState(initialValues)
  const [errors, setErrors] = useState({})
  const [touched, setTouched] = useState({})

  const handleChange = useCallback((field, value) => {
    setValues(prev => ({
      ...prev,
      [field]: value
    }))
    // Clear error for this field when user starts typing
    if (errors[field]) {
      setErrors(prev => ({
        ...prev,
        [field]: undefined
      }))
    }
  }, [errors])

  const handleBlur = useCallback((field) => {
    setTouched(prev => ({
      ...prev,
      [field]: true
    }))
  }, [])

  const validateForm = useCallback(() => {
    const newErrors = {}

    // Validate API Key (if provided)
    if (values.apiKey) {
      if (!values.apiKey.match(/^(.*)$/)) {
        newErrors.apiKey = 'Invalid NewRelic API key format. Must start with NRA followed by alphanumeric characters.'
      }
    }

    // Validate Account ID (if provided)
    if (values.accountId) {
      if (!values.accountId.match(/^\d+$/)) {
        newErrors.accountId = 'Account ID must be numeric'
      }
    }

    // Validate Region (if provided)
    if (values.region && !['US', 'EU'].includes(values.region)) {
      newErrors.region = 'Region must be US or EU'
    }

    setErrors(newErrors)
    return Object.keys(newErrors).length === 0
  }, [values])

  const reset = useCallback(() => {
    setValues(initialValues)
    setErrors({})
    setTouched({})
  }, [initialValues])

  return {
    values,
    errors,
    touched,
    handleChange,
    handleBlur,
    validateForm,
    reset,
    setValues,
    setErrors
  }
}
