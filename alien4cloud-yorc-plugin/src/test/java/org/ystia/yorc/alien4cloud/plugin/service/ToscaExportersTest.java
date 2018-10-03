package org.ystia.yorc.alien4cloud.plugin.service;

import java.nio.file.Paths;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

import javax.annotation.Resource;

import alien4cloud.component.ICSARRepositorySearchService;
import alien4cloud.model.components.CSARSource;
import alien4cloud.tosca.model.ArchiveRoot;
import alien4cloud.tosca.parser.ParsingError;
import alien4cloud.tosca.parser.ParsingErrorLevel;
import alien4cloud.tosca.parser.ParsingResult;
import alien4cloud.tosca.parser.ToscaParser;
import alien4cloud.tosca.parser.impl.ErrorCode;
import com.google.common.collect.Lists;
import org.alien4cloud.tosca.model.CSARDependency;
import org.alien4cloud.tosca.model.Csar;
import org.alien4cloud.tosca.model.definitions.DeploymentArtifact;
import org.alien4cloud.tosca.model.templates.Topology;
import org.alien4cloud.tosca.model.types.DataType;
import org.alien4cloud.tosca.model.types.NodeType;
import org.alien4cloud.tosca.model.types.RelationshipType;
import org.alien4cloud.tosca.normative.constants.NormativeCredentialConstant;
import org.apache.commons.io.FileUtils;
import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;
import org.mockito.Mockito;
import org.ystia.yorc.alien4cloud.plugin.AbstractPluginTest;

/**
 * A {@code ToscaExportersTest} is a ...
 *
 * @author Loic Albertin
 */
public class ToscaExportersTest extends AbstractPluginTest {

    @Resource
    protected ToscaComponentExporter toscaComponentExporter;
    @Resource
    private ICSARRepositorySearchService repositorySearchService;
    @Resource
    private ToscaTopologyExporter toscaTopologyExporter;
    @Resource(name = "yorc-tosca-parser")
    private ToscaParser parser;

    public static int countErrorByLevelAndCode(ParsingResult<?> parsingResult, ParsingErrorLevel errorLevel, ErrorCode errorCode) {
        int finalCount = 0;
        for (int i = 0; i < parsingResult.getContext().getParsingErrors().size(); i++) {
            ParsingError error = parsingResult.getContext().getParsingErrors().get(i);
            if (error.getErrorLevel().equals(errorLevel) && (error.getErrorCode().equals(errorCode) || errorCode == null)) {
                finalCount++;
            }
        }
        return finalCount;
    }

    public static void assertNoBlocker(ParsingResult<?> parsingResult) {
        Assert.assertFalse(countErrorByLevelAndCode(parsingResult, ParsingErrorLevel.ERROR, null) > 0);
    }

    @Test
    public void testComponentSerialization() throws Exception {
        Mockito.reset(repositorySearchService);
        String rootDir = "src/test/resources/org/ystia/yorc/alien4cloud/plugin/tosca";
        Csar csar = new Csar("tosca-normative-types", "1.0.0-ALIEN20");
        Mockito.when(repositorySearchService.getArchive(csar.getName(), csar.getVersion())).thenReturn(csar);
        NodeType mockedResult = Mockito.mock(NodeType.class);
        Mockito.when(mockedResult.getArchiveName()).thenReturn("tosca-normative-types");
        Mockito.when(mockedResult.getArchiveVersion()).thenReturn("1.0.0-ALIEN20");
        DeploymentArtifact da = Mockito.mock(DeploymentArtifact.class);
        Mockito.when(da.getArtifactPath()).thenReturn("test");
        Mockito.when(da.getArtifactRef()).thenReturn("test");
        Mockito.when(da.getArtifactType()).thenReturn("file");
        Mockito.when(da.getArchiveName()).thenReturn("tosca-normative-types");
        Mockito.when(da.getArchiveVersion()).thenReturn("1.0.0-ALIEN20");
        Mockito.when(mockedResult.getArtifacts()).thenReturn(Collections.singletonMap("SoftwareComponentArtifact", da));
        Mockito.when(repositorySearchService
                .getElementInDependencies(Mockito.eq(NodeType.class), Mockito.eq("tosca.nodes.SoftwareComponent"),
                        Mockito.any(Set.class))).thenReturn(mockedResult);
        Mockito.when(mockedResult.getDerivedFrom()).thenReturn(Lists.newArrayList("tosca.nodes.Root"));
        Mockito.when(repositorySearchService
                .getElementInDependencies(Mockito.eq(NodeType.class), Mockito.eq("tosca.nodes.Root"), Mockito.any(Set.class)))
                .thenReturn(mockedResult);

        Mockito.when(repositorySearchService
                .getElementInDependencies(Mockito.eq(NodeType.class), Mockito.eq("tosca.nodes.Compute"), Mockito.any(Set.class)))
                .thenReturn(mockedResult);
        Mockito.when(repositorySearchService
                .getElementInDependencies(Mockito.eq(DataType.class), Mockito.eq(NormativeCredentialConstant.DATA_TYPE),
                        Mockito.any(Set.class)))
                .thenReturn(Mockito.mock(DataType.class));

        RelationshipType hostedOn = new RelationshipType();
        Mockito.when(repositorySearchService
                .getElementInDependencies(Mockito.eq(RelationshipType.class), Mockito.eq("tosca.relationships.HostedOn"),
                        Mockito.any(Set.class))).thenReturn(hostedOn);


        ParsingResult<ArchiveRoot>
                parsingResult = parser.parseFile(Paths.get(rootDir, "tosca_component_input.yaml"));
        System.out.println(parsingResult.getContext().getParsingErrors());
        assertNoBlocker(parsingResult);

        String resultYaml = toscaComponentExporter.getYaml(parsingResult.getResult());
        System.out.println(resultYaml);
        String expectedResult = FileUtils.readFileToString(Paths.get(rootDir, "tosca_component_output.yaml").toFile(), "UTF-8");
        // Make some whitespaces change here as IDEs have auto-format features that will overwrite them in the file
        expectedResult = expectedResult.replaceAll("verbose:\\n", "verbose: \n");
        expectedResult = expectedResult.replaceAll("default:\\n", "default: \n");

        Assert.assertEquals(expectedResult, resultYaml);
    }

    @Test
    public void generateImports() {
        Mockito.reset(repositorySearchService);
        Csar csar = new Csar("tosca-normative-types", "2.0.0");
        csar.setImportSource(CSARSource.ALIEN.name());
        csar.setYamlFilePath("tosca-normative-types.yaml");
        Mockito.when(repositorySearchService.getArchive("tosca-normative-types", "2.0.0")).thenReturn(csar);
        csar = new Csar("yorc-types", "1.0.0");
        csar.setImportSource(CSARSource.ORCHESTRATOR.name());
        csar.setYamlFilePath("yorc-types.yaml");
        Mockito.when(repositorySearchService.getArchive("yorc-types", "1.0.0")).thenReturn(csar);
        csar = new Csar("yorc-openstack-types", "1.0.0");
        csar.setImportSource(CSARSource.ORCHESTRATOR.name());
        csar.setYamlFilePath("yorc-openstack-types.yaml");
        Mockito.when(repositorySearchService.getArchive("yorc-openstack-types", "1.0.0")).thenReturn(csar);
        csar = new Csar("mycomponent-pub", "3.0.0");
        csar.setImportSource(CSARSource.GIT.name());
        csar.setYamlFilePath("mycomponent-pub.yaml");
        Mockito.when(repositorySearchService.getArchive("mycomponent-pub", "3.0.0")).thenReturn(csar);
        csar = new Csar("mycomponent-impl", "3.0.0");
        csar.setImportSource(CSARSource.UPLOAD.name());
        csar.setYamlFilePath("mycomponent-impl.yaml");
        Mockito.when(repositorySearchService.getArchive("mycomponent-impl", "3.0.0")).thenReturn(csar);

        // Use linked hashset here to ensure ordering
        Set<CSARDependency> deps = new LinkedHashSet<>();
        deps.add(new CSARDependency("tosca-normative-types", "2.0.0"));
        deps.add(new CSARDependency("yorc-types", "1.0.0"));
        deps.add(new CSARDependency("yorc-openstack-types", "1.0.0"));
        deps.add(new CSARDependency("mycomponent-pub", "3.0.0"));
        deps.add(new CSARDependency("mycomponent-impl", "3.0.0"));

        csar = new Csar("mytopo", "1.0.0");
        csar.setTemplateAuthor("me");

        String expected = "tosca_definitions_version: alien_dsl_2_0_0\n" +
                "\n" +
                "metadata:\n" +
                "  template_name: mytopo\n" +
                "  template_version: 1.0.0\n" +
                "  template_author: me\n" +
                "\n" +
                "description: \"\"\n" +
                "\n" +
                "imports:\n" +
                "  - <normative-types.yml>\n" +
                "  - <yorc-types.yml>\n" +
                "  - <yorc-openstack-types.yml>\n" +
                "  - mycomponent-pub/3.0.0/mycomponent-pub.yaml\n" +
                "  - mycomponent-impl/3.0.0/mycomponent-impl.yaml\n" +
                "\n" +
                "topology_template:\n" +
                "  node_templates:\n";

        Topology topo = new Topology();
        topo.setDependencies(deps);

        String result = toscaTopologyExporter.getYaml(csar, topo, false);

        Assert.assertEquals(expected, result);
    }

    @Test
    public void TestTopologyExporter() throws Exception {
        Mockito.reset(repositorySearchService);
        Csar csar = new Csar("tosca-normative-types", "1.0.0-ALIEN20");
        csar.setImportSource(CSARSource.ALIEN.name());
        csar.setYamlFilePath("tosca-normative-types.yaml");
        Mockito.when(repositorySearchService.getArchive("tosca-normative-types", "1.0.0-ALIEN20")).thenReturn(csar);
        csar = new Csar("yorc-types", "1.0.0");
        csar.setImportSource(CSARSource.ORCHESTRATOR.name());
        csar.setYamlFilePath("yorc-types.yaml");
        Mockito.when(repositorySearchService.getArchive("yorc-types", "1.0.0")).thenReturn(csar);
        csar = new Csar("yorc-slurm-types", "1.0.0");
        csar.setImportSource(CSARSource.ORCHESTRATOR.name());
        csar.setYamlFilePath("yorc-slurm-types.yaml");
        Mockito.when(repositorySearchService.getArchive("yorc-slurm-types", "1.0.0")).thenReturn(csar);

        String rootDir = "src/test/resources/org/ystia/yorc/alien4cloud/plugin/tosca";
        ParsingResult<ArchiveRoot>
                parsingResult = parser.parseFile(Paths.get(rootDir, "tosca_topology_sample.yaml"));
        System.out.println(parsingResult.getContext().getParsingErrors());
        assertNoBlocker(parsingResult);

        Assert.assertNotNull(parsingResult.getResult().getRepositories());
        Assert.assertEquals(parsingResult.getResult().getRepositories().size(), 1);
        Assert.assertEquals(parsingResult.getResult().getRepositories().containsKey("docker"), true);

        Assert.assertNotNull(parsingResult.getResult().getTopology().getNodeTemplates());
        Assert.assertEquals(parsingResult.getResult().getTopology().getNodeTemplates().size(), 1);
        Assert.assertEquals(parsingResult.getResult().getTopology().getNodeTemplates().containsKey("Comp1"), true);
    }

    @Test
    public void TestTopologyExporterWithImplementationArtifactNotationToNotShorten() throws Exception {
        Mockito.reset(repositorySearchService);
        Csar csar = new Csar("tosca-normative-types", "1.0.0-ALIEN20");
        csar.setImportSource(CSARSource.ALIEN.name());
        csar.setYamlFilePath("tosca-normative-types.yaml");
        Mockito.when(repositorySearchService.getArchive("tosca-normative-types", "1.0.0-ALIEN20")).thenReturn(csar);
        csar = new Csar("yorc-types", "1.0.0");
        csar.setImportSource(CSARSource.ORCHESTRATOR.name());
        csar.setYamlFilePath("yorc-types.yaml");
        Mockito.when(repositorySearchService.getArchive("yorc-types", "1.0.0")).thenReturn(csar);
        csar = new Csar("yorc-slurm-types", "1.0.0");
        csar.setImportSource(CSARSource.ORCHESTRATOR.name());
        csar.setYamlFilePath("yorc-slurm-types.yaml");
        Mockito.when(repositorySearchService.getArchive("yorc-slurm-types", "1.0.0")).thenReturn(csar);

        String rootDir = "src/test/resources/org/ystia/yorc/alien4cloud/plugin/tosca";
        ParsingResult<ArchiveRoot>
                parsingResult = parser.parseFile(Paths.get(rootDir, "tosca_topology_with_implementation_artifact.yaml"));
        System.out.println(parsingResult.getContext().getParsingErrors());
        assertNoBlocker(parsingResult);

        Assert.assertNotNull(parsingResult.getResult().getTopology().getNodeTemplates());
        Assert.assertEquals(parsingResult.getResult().getTopology().getNodeTemplates().size(), 1);
        Assert.assertEquals(parsingResult.getResult().getTopology().getNodeTemplates().containsKey("Comp1"), true);

        Assert.assertNotNull(parsingResult.getResult().getTopology().getNodeTemplates().get("Comp1").getInterfaces());
        Assert.assertEquals(parsingResult.getResult().getTopology().getNodeTemplates().get("Comp1").getInterfaces().size(), 1);
        Assert.assertEquals(parsingResult.getResult().getTopology().getNodeTemplates().get("Comp1").getInterfaces().containsKey("tosca.interfaces.node.lifecycle.Runnable"), true);
        Assert.assertEquals(parsingResult.getResult().getTopology().getNodeTemplates().get("Comp1").getInterfaces().get("tosca.interfaces.node.lifecycle.Runnable").getOperations().size(), 1);
        Assert.assertEquals(parsingResult.getResult().getTopology().getNodeTemplates().get("Comp1").getInterfaces().get("tosca.interfaces.node.lifecycle.Runnable").getOperations().containsKey("run"), true);

        Assert.assertEquals(parsingResult.getResult().getTopology().getNodeTemplates().get("Comp1").getInterfaces().get("tosca.interfaces.node.lifecycle.Runnable").getOperations().get("run").getImplementationArtifact().getArtifactType(), "yorc.artifacts.Deployment.SlurmJobBin");
        Assert.assertEquals(parsingResult.getResult().getTopology().getNodeTemplates().get("Comp1").getInterfaces().get("tosca.interfaces.node.lifecycle.Runnable").getOperations().get("run").getImplementationArtifact().getArtifactRef(), "bin/submit.sh");
    }

}
