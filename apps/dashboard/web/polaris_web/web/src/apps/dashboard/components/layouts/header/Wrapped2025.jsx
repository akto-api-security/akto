import React, { useState, useEffect, useRef, useCallback } from 'react';
import './Wrapped2025.css';
import api from '../../../../signup/api';
import { Button, Icon } from '@shopify/polaris';
import { PlayMajor, PauseMajor, MobileCancelMajor } from '@shopify/polaris-icons';
import { mapLabel } from '../../../../main/labelHelper';
import PersistStore from '../../../../main/PersistStore';
import LocalStore from '../../../../main/LocalStorageStore';

const Wrapped2025 = ({ onClose }) => {
    const subCategoryMap = LocalStore(state => state.subCategoryMap);

    const [step, setStep] = useState(0);
    const [data, setData] = useState(null);
    const [loading, setLoading] = useState(true);
    const [playing, setPlaying] = useState(false);
    const audioRef = useRef(null);

    useEffect(() => {
        fetchData();
        // Try to play audio automatically if allowed, else wait for interaction
        const audio = new Audio('/public/wrapped_audio.mp3');
        audio.loop = true;
        audioRef.current = audio;

        const playPromise = audio.play();
        if (playPromise !== undefined) {
            playPromise.then(_ => {
                setPlaying(true);
            })
                .catch(error => {
                    setPlaying(false);
                });
        }

        return () => {
            if (audioRef.current) {
                audioRef.current.pause();
                audioRef.current = null;
            }
        }
    }, []);

    // ESC key listener to close modal
    useEffect(() => {
        const handleEscapeKey = (event) => {
            if (event.key === 'Escape') {
                fadeOutAndClose();
            }
        };

        document.addEventListener('keydown', handleEscapeKey);

        return () => {
            document.removeEventListener('keydown', handleEscapeKey);
        };
    }, [onClose, playing]);

    const fetchData = async () => {
        try {
            const resp = await api.fetchWrappedData();
            setData(resp.data || resp);
            setLoading(false);
        } catch (e) {
            console.error("Failed to fetch wrapped data", e);
            // Fallback mock data if API fails to avoid breaking UI during dev
            setData({
                newCollections: 0,
                totalTests: 0,
                criticalIssues: 0,
                teamSize: 1,
                year: 2025
            });
            setLoading(false);
        }
    };

    const fadeOutAndClose = useCallback(() => {
        if (audioRef.current && playing) {
            const audio = audioRef.current;
            const fadeOutDuration = 500; // ms
            const fadeOutSteps = 20;
            const stepDuration = fadeOutDuration / fadeOutSteps;
            const volumeStep = audio.volume / fadeOutSteps;

            let currentStep = 0;
            const fadeInterval = setInterval(() => {
                currentStep++;
                audio.volume = Math.max(0, audio.volume - volumeStep);

                if (currentStep >= fadeOutSteps || audio.volume <= 0) {
                    clearInterval(fadeInterval);
                    audio.pause();
                    audio.volume = 1; // Reset for next time
                    onClose();
                }
            }, stepDuration);
        } else {
            onClose();
        }
    }, [playing, onClose]);

    const toggleAudio = () => {
        if (audioRef.current) {
            if (playing) {
                audioRef.current.pause();
            } else {
                audioRef.current.play();
            }
            setPlaying(!playing);
        }
    }

    const nextStep = () => {
        if (step < slides.length - 1) {
            setStep(step + 1);
        } else {
            fadeOutAndClose();
        }
    };

    const prevStep = () => {
        if (step > 0) setStep(step - 1);
    }

    if (loading) return null;

    // Get current dashboard category for context-aware labels
    const dashboardCategory = PersistStore.getState().dashboardCategory || "API Security";

    // Helper functions for achievement messages
    const getTestCoverageMessage = (coverage) => {
        if (coverage >= 90) return "Security Champion! Nearly perfect coverage!";
        if (coverage >= 70) return "Great job! You're testing most of your APIs";
        if (coverage >= 50) return "Good start! More APIs to secure";
        return "Keep going! Let's improve that coverage";
    };

    const getFixedIssuesMessage = (fixedPercent) => {
        if (fixedPercent >= 80) return "Security Hero! Outstanding remediation rate";
        if (fixedPercent >= 50) return "Good progress on fixing vulnerabilities";
        if (fixedPercent >= 25) return "Making progress! Keep fixing those issues";
        return "Let's work on resolving those vulnerabilities";
    };

    const getVulnTypeDisplayName = (subCategory) => {
        return subCategoryMap?.[subCategory]?.testName || subCategory;
    };

    const getOverallAchievement = (testCoverage, fixedPercent, criticalIssues) => {
        if (testCoverage >= 80 && fixedPercent >= 70 && criticalIssues < 5) {
            return {
                title: "Security Superstar!",
                message: "Exceptional security posture across all metrics"
            };
        }
        if (testCoverage >= 60 && fixedPercent >= 50) {
            return {
                title: "Security Pro",
                message: "Strong security practices - keep it up!"
            };
        }
        if (testCoverage >= 40 || fixedPercent >= 30) {
            return {
                title: "Security Enthusiast",
                message: "You're on the right path to better security"
            };
        }
        return {
            title: "Security Journey Started",
            message: "Great potential - let's secure those APIs!"
        };
    };

    const slides = [
        {
            title: "2025 Wrapped",
            highlight: "Your Year in Review",
            text: "Let's see what you achieved with Akto this year!",
        },
        {
            title: mapLabel("API Collections", dashboardCategory),
            highlight: data?.newCollections || 0,
            text: `New ${mapLabel("API Collections", dashboardCategory)} created/discovered.`,
        },
        {
            title: mapLabel("API Endpoints Discovered", dashboardCategory),
            highlight: data?.totalEndpoints?.toLocaleString() || 0,
            text: `Your ${mapLabel("API", dashboardCategory)} inventory grew by ${data?.totalEndpoints || 0} ${mapLabel("endpoints", dashboardCategory)} this year!`,
        },
        {
            title: "Test Coverage",
            highlight: `${data?.testCoverage || 0}%`,
            text: getTestCoverageMessage(data?.testCoverage || 0),
            subtext: `${data?.testedEndpoints || 0} ${mapLabel("APIs", dashboardCategory)} tested out of ${data?.totalEndpoints || 0}`,
        },
        {
            title: mapLabel("Total Tests Run", dashboardCategory),
            highlight: data?.totalTests?.toLocaleString() || 0,
            text: `You've been busy securing your ${mapLabel("APIs", dashboardCategory)}!`,
        },
        {
            title: "Most Common Vulnerability",
            highlight: data?.topVulnCount > 0 ? data.topVulnCount : "None",
            text: data?.topVulnCount > 0
                ? `${getVulnTypeDisplayName(data?.topVulnType)} was your biggest challenge`
                : "No major vulnerabilities found - excellent work!",
        },
        {
            title: "Total Issues Found",
            highlight: data?.criticalIssues || 0,
            text: "Potential disasters averted.",
        },
        {
            title: "Issues Fixed",
            highlight: `${data?.fixedIssues || 0}`,
            text: getFixedIssuesMessage(data?.fixedPercent || 0),
            subtext: `${data?.fixedPercent || 0}% remediation rate`,
        },
        {
            title: "API Architecture",
            highlight: data?.primaryApiType || "REST",
            text: `${data?.primaryApiTypeCount || 0} ${data?.primaryApiType || "REST"} APIs discovered`,
            subtext: "Your dominant API architecture",
        },
        {
            title: "Team Growth",
            highlight: data?.teamSize || 1,
            text: "People working together on security.",
        },
        {
            title: getOverallAchievement(
                data?.testCoverage || 0,
                data?.fixedPercent || 0,
                data?.criticalIssues || 0
            ).title,
            highlight: "2026",
            text: getOverallAchievement(
                data?.testCoverage || 0,
                data?.fixedPercent || 0,
                data?.criticalIssues || 0
            ).message,
            subtext: "Here's to an even more secure year ahead!",
        }
    ];

    const currentSlide = slides[step];

    return (
        <div className="wrapped-overlay">
            <video
                className="wrapped-bg-video"
                autoPlay
                loop
                muted
                playsInline
                src="/public/wrapped_bg.mp4"
                onError={(e) => { e.target.style.display = 'none'; }}
            />
            <div className="wrapped-bg-fallback"></div>

            <div className="wrapped-close-btn-container">
                <button className="wrapped-close-btn" onClick={fadeOutAndClose}>
                    <Icon source={MobileCancelMajor} color="subdued" />
                </button>
            </div>

            <div className="wrapped-content" key={step}>
                <div className="wrapped-slide-title">{currentSlide.title}</div>
                <div className="wrapped-slide-stat">{currentSlide.highlight}</div>
                <div className="wrapped-slide-text">{currentSlide.text}</div>
                {currentSlide.subtext && (
                    <div className="wrapped-slide-subtext">{currentSlide.subtext}</div>
                )}
            </div>

            <div className="wrapped-controls">
                <button className="wrapped-btn" onClick={toggleAudio}>
                    {playing ? "Pause Music" : "Play Music"}
                </button>
                {step > 0 && <button className="wrapped-btn" onClick={prevStep}>Back</button>}
                <button className="wrapped-btn" onClick={nextStep}>
                    {step === slides.length - 1 ? "Finish" : "Next"}
                </button>
            </div>
        </div>
    );
};

export default Wrapped2025;
