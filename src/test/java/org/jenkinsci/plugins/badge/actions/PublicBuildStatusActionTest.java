package org.jenkinsci.plugins.badge.actions;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import hudson.ExtensionList;
import hudson.ExtensionPoint;
import hudson.model.FreeStyleProject;
import hudson.model.Job;
import hudson.model.Run;
import hudson.tasks.BatchFile;
import hudson.tasks.Shell;
import hudson.util.HttpResponses;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import org.jenkinsci.plugins.badge.extensionpoints.JobSelectorExtensionPoint;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;
import org.jvnet.hudson.test.JenkinsRule;

public class PublicBuildStatusActionTest {

    // JenkinsRule startup cost is high on Windows
    // Use a ClassRule to create one JenkinsRule used by all tests
    @ClassRule
    public static JenkinsRule j = new JenkinsRule();

    @Rule
    public TestName name = new TestName();

    private static final String SUCCESS_MARKER = "fill=\"#44cc11\"";
    private static final String NOT_RUN_MARKER = "fill=\"#9f9f9f\"";
    private static final String PASSING_MARKER = ">passing<";

    private FreeStyleProject job;
    private String jobStatusUrl;

    @Before
    public void createJob() throws IOException {
        // Give each job a name based on the name of the test method
        // Simplifies debugging and failure diagnosis
        // Also avoids any caching from reusing job name
        job = j.createFreeStyleProject("job-" + name.getMethodName());
        // Assure the job can pass on Windows and Unix
        job.getBuildersList()
                .add(
                        isWindows()
                                ? new BatchFile("echo hello from a batch file")
                                : new Shell("echo hello from a shell"));
        String statusUrl = j.getURL().toString() + "buildStatus/icon";
        jobStatusUrl = statusUrl + "?job=" + job.getName();
    }

    @Test
    public void testDoIconJobBefore() throws Exception {
        // Check job status icon is "not run" before job runs
        try (JenkinsRule.WebClient webClient = j.createWebClient()) {
            JenkinsRule.JSONWebResponse json = webClient.getJSON(jobStatusUrl);
            String result = json.getContentAsString();
            assertThat(result, containsString("<svg "));
            assertThat(result, not(containsString(SUCCESS_MARKER)));
            assertThat(result, containsString(NOT_RUN_MARKER));
        }
    }

    @Test
    public void testDoIconBuildBefore() throws Exception {
        String buildStatusUrl = jobStatusUrl + "&build=123";

        // Check build status icon is "not run" before job runs
        try (JenkinsRule.WebClient webClient = j.createWebClient()) {
            JenkinsRule.JSONWebResponse json = webClient.getJSON(buildStatusUrl);
            String result = json.getContentAsString();
            assertThat(result, containsString("<svg "));
            assertThat(result, not(containsString(SUCCESS_MARKER)));
            assertThat(result, containsString(NOT_RUN_MARKER));
        }
    }

    @Test
    public void testDoIconJobAfter() throws Exception {
        // Run the job, assert that it was successful
        Run<?, ?> build = job.scheduleBuild2(0).get();
        j.assertBuildStatusSuccess(build);

        // Check job status icon is correct after job runs successfully
        try (JenkinsRule.WebClient webClient = j.createWebClient()) {
            JenkinsRule.JSONWebResponse json = webClient.getJSON(jobStatusUrl);
            String result = json.getContentAsString();
            assertThat(result, containsString("<svg "));
            assertThat(result, containsString(SUCCESS_MARKER));
            assertThat(result, not(containsString(NOT_RUN_MARKER)));
        }
    }

    @Test
    public void testDoIconBuildAfter() throws Exception {
        // Run the job, assert that it was successful
        Run<?, ?> build = job.scheduleBuild2(0).get();
        j.assertBuildStatusSuccess(build);

        // Check build status icon is correct after job runs successfully
        String buildStatusUrl = jobStatusUrl + "&build=" + build.getNumber();
        try (JenkinsRule.WebClient webClient = j.createWebClient()) {
            JenkinsRule.JSONWebResponse json = webClient.getJSON(buildStatusUrl);
            String result = json.getContentAsString();
            assertThat(result, containsString("<svg "));
            assertThat(result, containsString(SUCCESS_MARKER));
            assertThat(result, not(containsString(NOT_RUN_MARKER)));
        }
    }

    @Test
    public void testGetUrlName() throws IOException {
        PublicBuildStatusAction action = new PublicBuildStatusAction();
        assertThat(action.getUrlName(), is("buildStatus"));
    }

    @Test
    public void testGetIconFileName() throws IOException {
        PublicBuildStatusAction action = new PublicBuildStatusAction();
        assertThat(action.getIconFileName(), is(nullValue()));
    }

    @Test
    public void testGetDisplayName() throws IOException {
        PublicBuildStatusAction action = new PublicBuildStatusAction();
        assertThat(action.getDisplayName(), is(nullValue()));
    }

    private boolean isWindows() {
        return File.pathSeparatorChar == ';';
    }

    @Test
    public void doText_shouldReturnMissingQueryParameterWhenJobIsNull() throws IOException {
        PublicBuildStatusAction action = new PublicBuildStatusAction();
        String result = action.doText(null, null, null, "123");
        assertThat(result, is("Missing query parameter: job"));
    }

    @Test
    public void doText_shouldReturnProjectIconWhenJobHasNotRun() throws IOException {
        PublicBuildStatusAction action = new PublicBuildStatusAction();
        String result = action.doText(null, null, job.getName(), null);
        assertThat(result, is(job.getIconColor().getDescription()));
        assertThat(result, is("Not built"));
    }

    @Test
    public void doText_shouldReturnProjectIconColorDescription() throws Exception {
        Run<?, ?> build = job.scheduleBuild2(0).get();
        j.assertBuildStatusSuccess(build);
        String result =
                new PublicBuildStatusAction().doText(null, null, job.getName(), String.valueOf(build.getNumber()));
        assertThat(result, is(job.getIconColor().getDescription()));
        assertThat(result, is("Success"));
    }

    @Test
    public void doText_shouldReturnRunIconColorDescription() throws Exception {
        Run<?, ?> build = job.scheduleBuild2(0).get();
        j.assertBuildStatusSuccess(build);
        String result =
                new PublicBuildStatusAction().doText(null, null, job.getName(), String.valueOf(build.getNumber()));
        assertThat(result, is(build.getIconColor().getDescription()));
        assertThat(result, is("Success"));
    }

    @Test
    public void doIconShouldReturnCorrectResponseForNullJob() throws Exception {
        try (JenkinsRule.WebClient webClient = j.createWebClient()) {
            String url = j.getURL().toString() + "buildStatus/icon";
            JenkinsRule.JSONWebResponse json = webClient.getJSON(url);
            String result = json.getContentAsString();
            // Surprising that build passing is reported without a job argument, but
            // that is the result with the current release
            assertThat(result, containsString(PASSING_MARKER));
        }
    }

    @Test
    public void doIconDotSvgShouldReturnCorrectResponseForNullJob() throws Exception {
        try (JenkinsRule.WebClient webClient = j.createWebClient()) {
            String url = j.getURL().toString() + "buildStatus/icon.svg";
            JenkinsRule.JSONWebResponse json = webClient.getJSON(url);
            String result = json.getContentAsString();
            // Surprising that build passing is reported without a job argument, but
            // that is the result with the current release
            assertThat(result, containsString(PASSING_MARKER));
        }
    }

    @Test
    public void doIconShouldReturnCorrectResponseForValidJob() throws Exception {
        Run<?, ?> build = job.scheduleBuild2(0).get();
        j.assertBuildStatusSuccess(build);
        try (JenkinsRule.WebClient webClient = j.createWebClient()) {
            String url = j.getURL().toString() + "buildStatus/icon?job=" + job.getName();
            JenkinsRule.JSONWebResponse json = webClient.getJSON(url);
            String result = json.getContentAsString();
            assertThat(result, containsString(PASSING_MARKER));
        }
    }

    @Test
    public void doIconDotSvgShouldReturnCorrectResponseForValidJob() throws Exception {
        Run<?, ?> build = job.scheduleBuild2(0).get();
        j.assertBuildStatusSuccess(build);
        try (JenkinsRule.WebClient webClient = j.createWebClient()) {
            String url = j.getURL().toString() + "buildStatus/icon.svg?job=" + job.getName();
            JenkinsRule.JSONWebResponse json = webClient.getJSON(url);
            String result = json.getContentAsString();
            assertThat(result, containsString(PASSING_MARKER));
        }
    }

    private Method getAccessibleGetProjectMethod() throws NoSuchMethodException {
        Method getProjectMethod =
                PublicBuildStatusAction.class.getDeclaredMethod("getProject", String.class, Boolean.class);
        getProjectMethod.setAccessible(true);
        return getProjectMethod;
    }

    private JobSelectorExtensionPoint createJobSelector(String jobName, Job<?, ?> mockJob) {
        return new JobSelectorExtensionPoint() {
            @Override
            public Job<?, ?> select(String selector) {
                return jobName.equals(selector) ? mockJob : null;
            }
        };
    }
    // Helper class to manage extension registration/cleanup
    class MockExtension<T extends ExtensionPoint> implements AutoCloseable {
        private final ExtensionList<T> extensions;
        private final T extension;

        public MockExtension(Class<T> extensionType, T extension) {
            this.extensions = ExtensionList.lookup(extensionType);
            this.extension = extension;
            this.extensions.add(extension);
        }

        @Override
        public void close() {
            extensions.remove(extension);
        }
    }

    @Test
    public void testGetProject() throws Exception {
        Method getProjectMethod = getAccessibleGetProjectMethod();

        Job<?, ?> mockJob = mock(Job.class);
        when(mockJob.hasPermission(PublicBuildStatusAction.VIEW_STATUS)).thenReturn(true);

        // Create test JobSelector
        JobSelectorExtensionPoint testSelector = createJobSelector("specialJob", mockJob);
        // Register the extension - using try-with-resources to ensure cleanup
        try (MockExtension<JobSelectorExtensionPoint> extension =
                new MockExtension<>(JobSelectorExtensionPoint.class, testSelector)) {

            // Invoke the private method
            Job<?, ?> result = (Job<?, ?>) getProjectMethod.invoke(null, "specialJob", false);

            assertNotNull(result, "Result should not be null");
            assertSame("Should return our mock job", mockJob, result);
        }
    }

    @Test
    public void testThrowErrorWhenNotFoundViaReflection() throws Exception {
        Method getProjectMethod = getAccessibleGetProjectMethod();

        try {
            getProjectMethod.invoke(null, "nonExistentJob", true);
            fail("Should have thrown an InvocationTargetException");
        } catch (InvocationTargetException e) {
            assertTrue(
                    e.getCause() instanceof HttpResponses.HttpResponseException,
                    "Cause should be HttpResponseException");
        }
    }

    @Test
    public void testNoPermissionViaReflection() throws Exception {
        Method getProjectMethod = getAccessibleGetProjectMethod();

        // Create a mock job without VIEW_STATUS permission
        Job<?, ?> mockJob = mock(Job.class);
        when(mockJob.hasPermission(PublicBuildStatusAction.VIEW_STATUS)).thenReturn(false);

        // Create test JobSelector that returns the no-permission job
        JobSelectorExtensionPoint testSelector = createJobSelector("noPermJob", mockJob);

        try (MockExtension<JobSelectorExtensionPoint> extension =
                new MockExtension<>(JobSelectorExtensionPoint.class, testSelector)) {

            // Test with throwErrorWhenNotFound = false
            Job<?, ?> result = (Job<?, ?>) getProjectMethod.invoke(null, "noPermJob", false);
            assertNull("Should return null for job without permission", result);

            // Test with throwErrorWhenNotFound = true
            try {
                getProjectMethod.invoke(null, "noPermJob", true);
                fail("Should have thrown an InvocationTargetException");
            } catch (InvocationTargetException e) {
                assertTrue(
                        e.getCause() instanceof HttpResponses.HttpResponseException,
                        "Cause should be HttpResponseException");
            }
        }
    }

    @Test
    public void testMultipleJobSelectorsViaReflection() throws Exception {
        Method getProjectMethod = getAccessibleGetProjectMethod();

        // Create two mock jobs
        Job<?, ?> mockJob1 = mock(Job.class);
        when(mockJob1.hasPermission(PublicBuildStatusAction.VIEW_STATUS)).thenReturn(true);

        Job<?, ?> mockJob2 = mock(Job.class);
        when(mockJob2.hasPermission(PublicBuildStatusAction.VIEW_STATUS)).thenReturn(true);

        // Create two different selectors
        JobSelectorExtensionPoint selector1 = createJobSelector("job1", mockJob1);
        JobSelectorExtensionPoint selector2 = createJobSelector("job2", mockJob2);
        try (MockExtension<JobSelectorExtensionPoint> ext1 =
                        new MockExtension<>(JobSelectorExtensionPoint.class, selector1);
                MockExtension<JobSelectorExtensionPoint> ext2 =
                        new MockExtension<>(JobSelectorExtensionPoint.class, selector2)) {

            // Test first selector
            Job<?, ?> result1 = (Job<?, ?>) getProjectMethod.invoke(null, "job1", false);
            assertSame("Should return first mock job", mockJob1, result1);

            // Test second selector
            Job<?, ?> result2 = (Job<?, ?>) getProjectMethod.invoke(null, "job2", false);
            assertSame("Should return second mock job", mockJob2, result2);
        }
    }
}
