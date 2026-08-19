<p align="center">
  <a href="https://oneocr.github.io/"><img src="https://oneocr.github.io/assets/mark.svg" width="76" height="76" alt="oneocr"></a>
</p>

<h1 align="center">oneocr-cli</h1>

<p align="center">The <code>1ocr</code> command — images, PDFs and whole folders to text, JSON, SVG and semantic XHTML.</p>

<p align="center">
  <a href="https://oneocr.github.io/">Website</a> ·
  <a href="https://github.com/oneocr">All repositories</a> ·
  <a href="https://oneocr.github.io/demo/sample-page.oneocr.xhtml">Live output</a>
</p>

---

## Requirements
- **API**: [oneocr-api](https://github.com/oneocr/api)
- **JDK 22+** (recommended) or JDK 21 with `--enable-preview` for runtime
- Windows 11

## Build
```bash
mvn clean package
```

## Usage
```bash
# Basic OCR - generates all formats by default (JSON, XHTML, SVG, TXT)
java --enable-native-access=ALL-UNNAMED -jar target/1ocr-2.0.jar image.jpg

# Verbose mode with informative output
java --enable-native-access=ALL-UNNAMED -jar target/1ocr-2.0.jar -v image.jpg

# Custom output files
java --enable-native-access=ALL-UNNAMED -jar target/1ocr-2.0.jar --json output.json --svg output.svg --xhtml output.xhtml --text output.txt image.jpg 

# Only specific formats (disable defaults)
java --enable-native-access=ALL-UNNAMED -jar target/1ocr-2.0.jar --no-defaults --xhtml document.xhtml image.jpg

# Confidence filtering and verbose output
java --enable-native-access=ALL-UNNAMED -jar target/1ocr-2.0.jar --min-confidence 0.8 --verbose image.jpg 
```

### Default Output Files
When no specific output files are specified, the tool generates:
- `image.jpg.oneocr.txt` - Plain text
- `image.jpg.oneocr.json` - JSON with metadata  
- `image.jpg.oneocr.xhtml` - XHTML format
- `image.jpg.oneocr.svg` - SVG visualization

## Features
- JSON export with metadata
- SVG visualization with bounding boxes
- Semantic XHTML5 output (see [format analysis](XHTML-Format-Analysis.md) for AI processing advantages)
- Plain text extraction
- Command-line interface

## Notes 
- Windows 11 Snipping tools OneOCR model (win11-oneocr) is the best price/quality/speed trade-off you can get for OCR. It's quality and multi-language OCR capabilities are state-of-art. 
- The win11-oneocr - is quiet light-weight, just 50MB dll and 50MB model, it does 1 page in 1 to 2 seconds giving a rate of 30K-50K pages per day using pure CPU (utilizing all cores) without requiring GPU. 
- The fact that you don't need a GPU is both a pro and con. Not able to leverage GPU limits scaling.  
- This tools module is however just an example implementation of the api, and serves as a fairly decent standalone simple tool. It lacks batch processing which is essential, otherwise for each image you would be re-loading the model each time which has a performance hit if you are planning to do millions of pages OCR with this.
- However there are examples which demonstrate how to load model once and do images in batch thus utilizing full CPU ( see TestJDK21JDK22Plus.java in api module). Batch processing in command line will be implemented soon.
- GraalVM native executable version is planned for future releases to simplify deployment, eliminate JDK dependency.
- To use this, you must first build the api module. We DO NOT provide pre-build jars or exes.

## Related Projects
- **API Module**: [oneocr-api](https://github.com/oneocr/api) - Core Java FFM bindings and native libraries
- **XHTML Presentation**: [semantic_xhtml](https://github.com/oneocr/semantic_xhtml) - Interactive CSS/JS for XHTML format
- **Original Research**: [win11-oneocr](https://github.com/b1tg/win11-oneocr) - Initial reverse engineering of Windows 11 OneOCR

## Project Ecosystem
```
oneocr-api (Core FFM bindings)
    ↓
oneocr-cli (Command-line tools) → Generates XHTML
    ↓
semantic_xhtml (Presentation layer) → Renders XHTML
```