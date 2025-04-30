function nameSuffixes(tests) {
    return Object.entries(tests)
        .sort()
        .filter(category => {
            let selectedCount = 0
            category[1].forEach(test => {
                if (test.selected) selectedCount += 1
            })

            return selectedCount > 0
        })
        .map(category => category[0])
}

function convertToLowerCaseWithUnderscores(inputString) {
    if (!inputString)
        return ""
    return inputString?.toLowerCase()?.replace(/\s+/g, '_');
}

const createTestName = (collectionName, tests, activeFromTesting, ogTestName) => {
    if (activeFromTesting) {
        return ogTestName
    }
    let testName = convertToLowerCaseWithUnderscores(collectionName);
    if (tests && tests instanceof Object && Object.keys(tests).length > 0) {
        testName += "_" + nameSuffixes(tests).join("_")
    }
    return testName;
}

export { createTestName, convertToLowerCaseWithUnderscores };