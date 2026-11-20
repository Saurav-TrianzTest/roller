#!/usr/bin/env python3
"""Fix SharedThemeFromDir.java - replace File usage with S3/classpath"""
import re

filepath = "app/src/main/java/org/apache/roller/weblogger/business/themes/SharedThemeFromDir.java"

with open(filepath, 'r') as f:
    content = f.read()

# Add loadThemeResourceBytes helper method before loadTemplateFile
helper_method = '''
    /**
     * Load a theme resource as bytes from classpath or S3.
     */
    private byte[] loadThemeResourceBytes(String resourcePath) {
        // cr-java-0061/0063: Read from classpath first (ships with app), then S3
        String classpathPath = "themes/" + this.themeDir + "/" + resourcePath;
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(classpathPath)) {
            if (is != null) {
                return is.readAllBytes();
            }
        } catch (IOException e) {
            log.debug("Classpath resource not found: " + classpathPath, e);
        }

        // Try S3
        try {
            S3Client s3 = S3Client.create();
            String bucket = System.getenv().getOrDefault("APP_S3_BUCKET", "roller-themes");
            String key = this.themeDir + "/" + resourcePath;
            GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .build();
            try (InputStream is = s3.getObject(getObjectRequest)) {
                return is.readAllBytes();
            }
        } catch (SdkException e) {
            log.warn("Could not load resource from S3: " + resourcePath, e);
        } catch (IOException e) {
            log.warn("IO error loading resource: " + resourcePath, e);
        }
        return null;
    }

'''

# Replace the templateFile-based loadTemplateFile with a path-based version
old_load_template = '''    private String loadTemplateFile(File templateFile) {
        // Continue reading theme even if problem encountered with one file
        if (!templateFile.exists() && !templateFile.canRead()) {
            return null;
        }

        char[] chars;
        int length;
        try {
            chars = new char[(int) templateFile.length()];
            FileInputStream stream = new FileInputStream(templateFile);
            InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8);
            length = reader.read(chars);
        } catch (Exception noprob) {
            log.error("Exception reading theme [" + this.getName()
                    + "] template file [" + templateFile + "]");
            if (log.isDebugEnabled()) {
                log.debug(noprob);
            }
            return null;
        }

        return new String(chars, 0, length);
    }'''

new_load_template = '''    private String loadTemplateFile(String resourcePath) {
        // cr-java-0061/0063: Replace java.io.File with S3/classpath
        byte[] bytes = loadThemeResourceBytes(resourcePath);
        if (bytes == null) {
            return null;
        }
        return new String(bytes, 0, bytes.length, StandardCharsets.UTF_8);
    }'''

content = content.replace(old_load_template, new_load_template)

# Add helper method before loadTemplateFile
content = content.replace(
    '    private String loadTemplateFile(String resourcePath) {',
    helper_method + '    private String loadTemplateFile(String resourcePath) {'
)

# Replace File templateFile usages in loadThemeFromDisk
# Preview image
content = content.replace(
    '''        // load resource representing preview image
        // cr-java-0061/0063: Replace java.io.File with S3 object storage
        byte[] previewBytes = loadThemeResourceBytes(themeMetadata.getPreviewImage());
        if (previewBytes == null) {
            log.warn("Couldn't read theme [" + this.getName()
                    + "] preview image file ["
                    + themeMetadata.getPreviewImage() + "]");
        } else {
            this.previewImage = new SharedThemeResourceFromDir(
                    themeMetadata.getPreviewImage(), previewBytes);
        }''',
    '''        // load resource representing preview image
        // cr-java-0061/0063: Replace java.io.File with S3 object storage
        byte[] previewBytes = loadThemeResourceBytes(themeMetadata.getPreviewImage());
        if (previewBytes == null) {
            log.warn("Couldn't read theme [" + this.getName()
                    + "] preview image file ["
                    + themeMetadata.getPreviewImage() + "]");
        } else {
            this.previewImage = new SharedThemeResourceFromDir(
                    themeMetadata.getPreviewImage(), previewBytes);
        }'''
)

# Replace stylesheet File templateFile
content = content.replace(
    '''            // construct File object from path
            // we are getting the file path from standard as the default and
            // load it to initially.
            File templateFile = new File(this.themeDir + File.separator
                    + standardTemplateCode.getContentsFile());

            // read stylesheet contents
            String contents = loadTemplateFile(templateFile);
            if (contents == null) {
                // if we don't have any contents then skip this one
                log.error("Couldn't load stylesheet theme [" + this.getName()
                        + "] template file [" + templateFile + "]");
            } else {''',
    '''            // cr-java-0061/0063: Replace java.io.File with S3/classpath
            String templateFilePath = standardTemplateCode.getContentsFile();

            // read stylesheet contents
            String contents = loadTemplateFile(templateFilePath);
            if (contents == null) {
                // if we don't have any contents then skip this one
                log.error("Couldn't load stylesheet theme [" + this.getName()
                        + "] template file [" + templateFilePath + "]");
            } else {'''
)

# Replace lastModified for stylesheet
content = content.replace(
    '''                        stylesheetTmpl.getLink(), new Date(
                                templateFile.lastModified()), false, false);''',
    '''                        stylesheetTmpl.getLink(), new Date(
                                System.currentTimeMillis()), false, false);'''
)

# Replace resource File
content = content.replace(
    '''        // go through static resources and add them to the theme
        for (String resourcePath : themeMetadata.getResources()) {
            // construct ThemeResource object from resource
            File resourceFile = new File(this.themeDir + File.separator
                    + resourcePath);

            // Continue reading theme even if problem encountered with one file
            if (!resourceFile.exists() || !resourceFile.canRead()) {
                log.warn("Couldn't read  theme [" + this.getName()
                        + "] resource file [" + resourcePath + "]");
                continue;
            }

            // add it to the theme
            setResource(resourcePath, new SharedThemeResourceFromDir(
                    resourcePath, resourceFile));

            // Set Last Modified
            Date lstModified = new Date(resourceFile.lastModified());''',
    '''        // go through static resources and add them to the theme
        for (String resourcePath : themeMetadata.getResources()) {
            // cr-java-0061/0063: Replace java.io.File with S3/classpath
            byte[] resourceBytes = loadThemeResourceBytes(resourcePath);

            // Continue reading theme even if problem encountered with one file
            if (resourceBytes == null) {
                log.warn("Couldn't read  theme [" + this.getName()
                        + "] resource file [" + resourcePath + "]");
                continue;
            }

            // add it to the theme
            setResource(resourcePath, new SharedThemeResourceFromDir(
                    resourcePath, resourceBytes));

            // Set Last Modified
            Date lstModified = new Date(System.currentTimeMillis());'''
)

# Replace template File in templates loop
content = content.replace(
    '''            // construct File object from path
            File templateFile = new File(this.themeDir + File.separator
                    + standardTemplateCode.getContentsFile());

            String contents = loadTemplateFile(templateFile);
            if (contents == null) {
                // if we don't have any contents then skip this one
                throw new ThemeInitializationException("Couldn't load theme ["
                        + this.getName() + "] template file [" + templateFile
                        + "]");
            }

            // construct ThemeTemplate representing this file
            themeTemplate = new SharedThemeTemplate(
                    themeMetadata.getId() + ":" + templateMetadata.getName(),
                    templateMetadata.getAction(), templateMetadata.getName(),
                    templateMetadata.getDescription(), contents,
                    templateMetadata.getLink(), new Date(
                            templateFile.lastModified()),''',
    '''            // cr-java-0061/0063: Replace java.io.File with S3/classpath
            String templateFilePath = standardTemplateCode.getContentsFile();

            String contents = loadTemplateFile(templateFilePath);
            if (contents == null) {
                // if we don't have any contents then skip this one
                throw new ThemeInitializationException("Couldn't load theme ["
                        + this.getName() + "] template file [" + templateFilePath
                        + "]");
            }

            // construct ThemeTemplate representing this file
            themeTemplate = new SharedThemeTemplate(
                    themeMetadata.getId() + ":" + templateMetadata.getName(),
                    templateMetadata.getAction(), templateMetadata.getName(),
                    templateMetadata.getDescription(), contents,
                    templateMetadata.getLink(), new Date(
                            System.currentTimeMillis()),'''
)

# Replace createRendition File usage
content = content.replace(
    '''        // construct File object from path
        File templateFile = new File(this.themeDir + File.separator
                + templateCodeMetadata.getContentsFile());

        // read stylesheet contents
        String contents = loadTemplateFile(templateFile);
        if (contents == null) {
            // if we don't have any contents then load no string
            contents = "";
            log.error("Couldn't load stylesheet theme [" + this.getName()
                    + "] template file [" + templateFile + "]");
        }
        //TODO: remove templateId above
        templateRendition.setTemplate(contents);
        templateRendition.setTemplateLanguage(templateCodeMetadata.getTemplateLang());
        templateRendition.setType(templateCodeMetadata.getType());
        templateRendition.setLastModified(new Date(templateFile.lastModified()));''',
    '''        // cr-java-0061/0063: Replace java.io.File with S3/classpath
        String templateFilePath = templateCodeMetadata.getContentsFile();

        // read stylesheet contents
        String contents = loadTemplateFile(templateFilePath);
        if (contents == null) {
            // if we don't have any contents then load no string
            contents = "";
            log.error("Couldn't load stylesheet theme [" + this.getName()
                    + "] template file [" + templateFilePath + "]");
        }
        //TODO: remove templateId above
        templateRendition.setTemplate(contents);
        templateRendition.setTemplateLanguage(templateCodeMetadata.getTemplateLang());
        templateRendition.setType(templateCodeMetadata.getType());
        templateRendition.setLastModified(new Date(System.currentTimeMillis()));'''
)

# Remove unused File import
content = content.replace('import java.io.File;\n', '')
content = content.replace('import java.io.FileInputStream;\n', '')

with open(filepath, 'w') as f:
    f.write(content)

print("Done fixing SharedThemeFromDir.java")
