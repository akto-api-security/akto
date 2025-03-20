const fs = require('fs');
const path = require('path');

// Read all files in current directory
const files = fs.readdirSync(__dirname);

// Filter for SVG files and extract country codes
const flagFiles = files
    .filter(file => file.endsWith('.svg'))
    .map(file => path.basename(file, '.svg'));

// Generate content for index.mjs
let content = '// Auto-generated file - DO NOT EDIT\n\n';

// Add imports
flagFiles.forEach(countryCode => {
    content += `import ${countryCode} from './${countryCode}.svg';\n`;
});

// Add exports
content += '\nexport const flags = {\n';
flagFiles.forEach(countryCode => {
    content += `    "${countryCode}": ${countryCode},\n`;
});
content += '};\n';

// Write to index.mjs
fs.writeFileSync(
    path.join(__dirname, 'index.mjs'),
    content,
    'utf8'
);

console.log(`Generated index.mjs with ${flagFiles.length} flags`);
