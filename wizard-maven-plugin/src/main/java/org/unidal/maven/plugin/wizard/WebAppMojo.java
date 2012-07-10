package org.unidal.maven.plugin.wizard;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.project.MavenProject;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.Namespace;
import org.jdom.input.SAXBuilder;
import org.jdom.output.Format;
import org.jdom.output.XMLOutputter;
import org.unidal.codegen.generator.AbstractGenerateContext;
import org.unidal.codegen.generator.GenerateContext;
import org.unidal.codegen.generator.Generator;
import org.unidal.codegen.meta.WizardMeta;
import org.unidal.maven.plugin.common.PropertyProviders;
import org.unidal.maven.plugin.wizard.model.entity.Module;
import org.unidal.maven.plugin.wizard.model.entity.Page;
import org.unidal.maven.plugin.wizard.model.entity.Webapp;
import org.unidal.maven.plugin.wizard.model.entity.Wizard;
import org.unidal.maven.plugin.wizard.model.transform.DefaultDomParser;
import org.xml.sax.SAXException;

import com.site.helper.Files;

/**
 * Create a new page of web application project.
 * 
 * @goal webapp
 */
public class WebAppMojo extends AbstractMojo {
   /**
    * Current project
    * 
    * @parameter expression="${project}"
    * @required
    * @readonly
    */
   protected MavenProject m_project;

   /**
    * Current project base directory
    * 
    * @parameter expression="${basedir}"
    * @required
    * @readonly
    */
   protected File baseDir;

   /**
    * XSL code generator implementation
    * 
    * @component role="org.unidal.codegen.generator.Generator"
    *            role-hint="wizard-webapp"
    * @required
    * @readonly
    */
   protected Generator m_generator;

   /**
    * Wizard meta component
    * 
    * @component
    * @required
    * @readonly
    */
   protected WizardMeta m_meta;

   /**
    * Current project base directory
    * 
    * @parameter expression="${sourceDir}" default-value="${basedir}"
    * @required
    */
   protected String sourceDir;

   /**
    * Location of manifest.xml file
    * 
    * @parameter expression="${manifest}" default-value=
    *            "${basedir}/src/main/resources/META-INF/wizard/webapp/manifest.xml"
    * @required
    */
   protected String manifest;

   /**
    * Location of generated source directory
    * 
    * @parameter expression="${resource.base}"
    *            default-value="/META-INF/wizard/webapp"
    * @required
    */
   protected String resouceBase;

   /**
    * Verbose information or not
    * 
    * @parameter expression="${verbose}" default-value="false"
    */
   protected boolean verbose;

   /**
    * Verbose information or not
    * 
    * @parameter expression="${debug}" default-value="false"
    */
   protected boolean debug;

   protected Wizard buildWizard(File wizardFile) throws IOException, SAXException {
      Wizard wizard;

      if (wizardFile.isFile()) {
         String content = Files.forIO().readFrom(wizardFile, "utf-8");

         wizard = new DefaultDomParser().parse(content);
      } else {
         Webapp webapp = new Webapp();

         String packageName = PropertyProviders.fromConsole().forString("package", "Java package name for webapp:", null, null);
         String defaultName = packageName.substring(packageName.lastIndexOf('.') + 1);
         String name = PropertyProviders.fromConsole().forString("name", "Name for webapp:", defaultName, null);
         boolean webres = PropertyProviders.fromConsole().forBoolean("webres", "Support WebRes framework?", false);
         boolean cat = PropertyProviders.fromConsole().forBoolean("cat", "Support CAT?", true);

         wizard = new Wizard();
         wizard.setWebapp(webapp);
         webapp.setPackage(packageName);
         webapp.setName(name);
         webapp.setWebres(webres);
         webapp.setCat(cat);
      }

      Webapp webapp = wizard.getWebapp();
      List<Module> modules = webapp.getModules();
      List<String> moduleNames = new ArrayList<String>(modules.size());

      for (Module module : modules) {
         moduleNames.add(module.getName());
      }

      String moduleName = PropertyProviders.fromConsole().forString("module", "Select module name below or input a new one:",
            moduleNames, null, null);
      Module module = webapp.findModule(moduleName);

      if (module == null) {
         String path = PropertyProviders.fromConsole().forString("path", "Module path:", moduleName.substring(0, 1), null);

         module = new Module(moduleName);

         module.setPath(path);
         module.setDefault(modules.isEmpty());
         webapp.addModule(module);
      }

      List<String> pageNames = new ArrayList<String>(module.getPages().size());

      for (Page page : module.getPages()) {
         pageNames.add(page.getName());
      }

      String pageName = PropertyProviders.fromConsole().forString("page", "Select page name below or input a new one:", pageNames,
            null, null);
      Page page = module.findPage(pageName);

      if (page == null) {
         String path = PropertyProviders.fromConsole().forString("path", "Page path:", pageName, null);

         page = new Page(pageName);

         if (module.getPages().isEmpty()) {
            page.setDefault(true);
         }

         String caption = Character.toUpperCase(pageName.charAt(0)) + pageName.substring(1);

         page.setPath(path);
         page.setTitle(caption);
         page.setDescription(caption);
         module.addPage(page);
      }

      return wizard;
   }

   public void execute() throws MojoExecutionException, MojoFailureException {
      try {
         final File manifestFile = getFile(manifest);
         File wizardFile = new File(manifestFile.getParentFile(), "wizard.xml");
         Reader reader = new StringReader(buildWizard(wizardFile).toString());

         if (!manifestFile.exists()) {
            saveXml(m_meta.getManifest("wizard.xml"), manifestFile);
         }

         saveXml(m_meta.getWizard(reader), wizardFile);

         final URL manifestXml = manifestFile.toURI().toURL();
         final GenerateContext ctx = new AbstractGenerateContext(m_project.getBasedir(), resouceBase, sourceDir) {
            public URL getManifestXml() {
               return manifestXml;
            }

            public void log(LogLevel logLevel, String message) {
               switch (logLevel) {
               case DEBUG:
                  if (debug) {
                     getLog().debug(message);
                  }
                  break;
               case INFO:
                  if (debug || verbose) {
                     getLog().info(message);
                  }
                  break;
               case ERROR:
                  getLog().error(message);
                  break;
               }
            }
         };

         m_generator.generate(ctx);
         m_project.addCompileSourceRoot(sourceDir);
         getLog().info(ctx.getGeneratedFiles() + " files generated.");

         addDependenciesToPom(m_project.getFile());
      } catch (Exception e) {
         throw new MojoExecutionException("Code generating failed.", e);
      }
   }

   protected void addDependenciesToPom(File pomFile) throws Exception {
      Document doc = new SAXBuilder().build(pomFile);
      Element root = doc.getRootElement();
      Namespace ns = Namespace.getNamespace("http://maven.apache.org/POM/4.0.0");
      Element packaging = findOrCreateChild(ns, root, "packaging");

      packaging.setText("war");

      Element dependencies = findOrCreateChild(ns, root, "dependencies");

      if (!checkDependency(ns, dependencies, "com.site.common", "web-framework", "1.0.12", null)) {
         checkDependency(ns, dependencies, "com.site.common", "test-framework", "1.0.1", "test");
         checkDependency(ns, dependencies, "javax.servlet", "servlet-api", "2.5", "provided");
         checkDependency(ns, dependencies, "junit", "junit", "4.8.1", "test");
         checkDependency(ns, dependencies, "org.mortbay.jetty", "jetty", "6.1.14", "test");
         checkDependency(ns, dependencies, "org.mortbay.jetty", "jsp-2.1", "6.1.14", "test");
      }
   }

   @SuppressWarnings("unchecked")
   private boolean checkDependency(Namespace ns, Element dependencies, String groupId, String artifactId, String version,
         String scope) {
      List<Element> children = dependencies.getChildren("dependency", ns);
      Element dependency = null;

      for (Element child : children) {
         String g = child.getChildText("groupId", ns);
         String a = child.getChildText("artifactId", ns);

         if (groupId.equals(g) && artifactId.equals(a)) {
            dependency = child;
            break;
         }
      }

      if (dependency == null) {
         dependency = new Element("dependency", ns);
         createChild(ns, dependency, "groupId", groupId);
         createChild(ns, dependency, "artifactId", artifactId);
         createChild(ns, dependency, "version", version);

         if (scope != null) {
            createChild(ns, dependency, "scope", scope);
         }

         dependencies.addContent(dependency);
         return false;
      } else {
         return true;
      }
   }

   private void createChild(Namespace ns, Element parent, String name, String value) {
      Element child = new Element(name, ns).setText(value);

      parent.addContent(child);
   }

   private Element findOrCreateChild(Namespace ns, Element parent, String name) {
      Element child = parent.getChild(name, ns);

      if (child == null) {
         child = new Element(name, ns);
         parent.addContent(child);
      }

      return child;
   }

   protected File getFile(String path) {
      File file;

      if (path.startsWith("/") || path.indexOf(':') > 0) {
         file = new File(path);
      } else {
         file = new File(baseDir, path);
      }

      return file;
   }

   protected void saveXml(Document doc, File file) throws IOException {
      File parent = file.getCanonicalFile().getParentFile();

      if (!parent.exists()) {
         parent.mkdirs();
      }

      Format format = Format.getPrettyFormat();
      XMLOutputter outputter = new XMLOutputter(format);
      FileWriter writer = new FileWriter(file);

      try {
         outputter.output(doc, writer);
         getLog().info("File " + file.getCanonicalPath() + " generated.");
      } finally {
         writer.close();
      }
   }
}
