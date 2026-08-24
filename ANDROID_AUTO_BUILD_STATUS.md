# Android Auto foundation build

Result: **FAIL**

```text
Fetching distribution.
Downloading https://services.gradle.org/distributions/gradle-9.7.1-bin.zip
..............10%..............20%...............30%..............40%...............50%..............60%...............70%..............80%..............90%...............100%

Welcome to Gradle 9.7.1!

Here are the highlights of this release:
 - Isolated Projects graduates to incubating
 - Broader Configuration Cache compatibility
 - Resilient Sync helps you fix broken builds
 - More source locations in problem reports

For more details see https://docs.gradle.org/9.7.1/release-notes.html

Starting a Gradle Daemon (subsequent builds will be faster)
Configuration on demand is an incubating feature.

> Configure project :app
WARNING: The option setting 'android.newDsl=false' is deprecated.
The current default is 'true'.
It will be removed in version 10.0 of the Android Gradle plugin.
Add android.sync.suppressAgpWarnings=UNSUPPORTED_PROJECT_OPTION_USE to the gradle.properties file to suppress this warning.
WARNING: The option setting 'android.enableJetifier=true' is deprecated.
The current default is 'false'.
It will be removed in version 10.0 of the Android Gradle plugin.
Add android.sync.suppressAgpWarnings=UNSUPPORTED_PROJECT_OPTION_USE to the gradle.properties file to suppress this warning.
w: file:///home/runner/work/Nextcloud_Talk_Android_Auto/Nextcloud_Talk_Android_Auto/app/build.gradle.kts:25:42: 'fun ExtraPropertiesExtension.provideDelegate(receiver: Any?, property: KProperty<*>): MutablePropertyDelegate' is deprecated. Use 'val property = extra[name] as Type' instead. See the Gradle 9.6 upgrading guide.
w: file:///home/runner/work/Nextcloud_Talk_Android_Auto/Nextcloud_Talk_Android_Auto/app/build.gradle.kts:45:1: 'fun Project.android(configure: Action<BaseAppModuleExtension>): Unit' is deprecated. Replaced by com.android.build.api.dsl.ApplicationExtension.
This class is not used for the public extensions in AGP when android.newDsl=true, which is the default in AGP 9.0, and will be removed in AGP 10.0.
w: file:///home/runner/work/Nextcloud_Talk_Android_Auto/Nextcloud_Talk_Android_Auto/app/build.gradle.kts:102:41: 'fun srcDir(srcDir: Any): Any' is deprecated. Use `directories` mutable set instead.
w: file:///home/runner/work/Nextcloud_Talk_Android_Auto/Nextcloud_Talk_Android_Auto/app/build.gradle.kts:161:9: 'var htmlOutput: File?' is deprecated. Use SingleArtifact.LINT_HTML_REPORT or SingleArtifact.AGGREGATED_LINT_HTML_REPORT to cconsume lint report artifacts.
w: file:///home/runner/work/Nextcloud_Talk_Android_Auto/Nextcloud_Talk_Android_Auto/app/build.gradle.kts:162:9: 'var htmlReport: Boolean' is deprecated. Lint reports are now always generated. Use SingleArtifact.LINT_HTML_REPORT or SingleArtifact.AGGREGATED_LINT_HTML_REPORT to consume lint report artifacts.

> Task :app:preBuild UP-TO-DATE
> Task :app:preGplayDebugBuild UP-TO-DATE
> Task :app:dataBindingMergeDependencyArtifactsGplayDebug
> Task :app:dataBindingMergeDependencyArtifactsGplayDebug FAILED

[Incubating] Problems report is available at: file:///home/runner/work/Nextcloud_Talk_Android_Auto/Nextcloud_Talk_Android_Auto/build/reports/problems/problems-report.html

FAILURE: Build failed with an exception.

* What went wrong:
Execution failed for task ':app:dataBindingMergeDependencyArtifactsGplayDebug' (registered by plugin 'com.android.internal.application').
> Could not resolve all files for configuration ':app:gplayDebugCompileClasspath'.
   > Could not find androidx.core:core-telecom:1.1.0-beta01.
     Required by:
         project ':app'

* Try:
> Run with --info or --debug option to get more log output.
> Run with --scan to get full insights from a Build Scan (powered by Develocity).
> Get more help at https://help.gradle.org.

* Exception is:
org.gradle.api.tasks.TaskExecutionException: Execution failed for task ':app:dataBindingMergeDependencyArtifactsGplayDebug' (registered by plugin 'com.android.internal.application').
	at org.gradle.api.internal.tasks.execution.CatchExceptionTaskExecuter.execute(CatchExceptionTaskExecuter.java:38)
	at org.gradle.api.internal.tasks.execution.EventFiringTaskExecuter$1.executeTask(EventFiringTaskExecuter.java:77)
	at org.gradle.api.internal.tasks.execution.EventFiringTaskExecuter$1.call(EventFiringTaskExecuter.java:55)
	at org.gradle.api.internal.tasks.execution.EventFiringTaskExecuter$1.call(EventFiringTaskExecuter.java:52)
	at org.gradle.internal.operations.DefaultBuildOperationRunner$CallableBuildOperationWorker.execute(DefaultBuildOperationRunner.java:210)
	at org.gradle.internal.operations.DefaultBuildOperationRunner$CallableBuildOperationWorker.execute(DefaultBuildOperationRunner.java:205)
	at org.gradle.internal.operations.DefaultBuildOperationRunner$2.execute(DefaultBuildOperationRunner.java:67)
	at org.gradle.internal.operations.DefaultBuildOperationRunner$2.execute(DefaultBuildOperationRunner.java:60)
	at org.gradle.internal.operations.DefaultBuildOperationRunner.execute(DefaultBuildOperationRunner.java:167)
	at org.gradle.internal.operations.DefaultBuildOperationRunner.execute(DefaultBuildOperationRunner.java:60)
	at org.gradle.internal.operations.DefaultBuildOperationRunner.call(DefaultBuildOperationRunner.java:54)
	at org.gradle.api.internal.tasks.execution.EventFiringTaskExecuter.execute(EventFiringTaskExecuter.java:52)
	at org.gradle.execution.plan.DefaultNodeExecutor.executeLocalTaskNode(DefaultNodeExecutor.java:55)
	at org.gradle.execution.plan.DefaultNodeExecutor.execute(DefaultNodeExecutor.java:34)
	at org.gradle.execution.taskgraph.DefaultTaskExecutionGraph$InvokeNodeExecutorsAction.execute(DefaultTaskExecutionGraph.java:355)
	at org.gradle.execution.taskgraph.DefaultTaskExecutionGraph$InvokeNodeExecutorsAction.execute(DefaultTaskExecutionGraph.java:343)
	at org.gradle.execution.taskgraph.DefaultTaskExecutionGraph$BuildOperationAwareExecutionAction.lambda$execute$0(DefaultTaskExecutionGraph.java:339)
	at org.gradle.internal.operations.CurrentBuildOperationRef.with(CurrentBuildOperationRef.java:84)
	at org.gradle.execution.taskgraph.DefaultTaskExecutionGraph$BuildOperationAwareExecutionAction.execute(DefaultTaskExecutionGraph.java:339)
	at org.gradle.execution.taskgraph.DefaultTaskExecutionGraph$BuildOperationAwareExecutionAction.execute(DefaultTaskExecutionGraph.java:328)
	at org.gradle.execution.plan.DefaultPlanExecutor$ExecutorWorker.execute(DefaultPlanExecutor.java:459)
	at org.gradle.execution.plan.DefaultPlanExecutor$ExecutorWorker.run(DefaultPlanExecutor.java:376)
	at org.gradle.execution.plan.DefaultPlanExecutor.process(DefaultPlanExecutor.java:111)
	at org.gradle.execution.taskgraph.DefaultTaskExecutionGraph.executeWithServices(DefaultTaskExecutionGraph.java:146)
	at org.gradle.execution.taskgraph.DefaultTaskExecutionGraph.execute(DefaultTaskExecutionGraph.java:131)
	at org.gradle.execution.SelectedTaskExecutionAction.execute(SelectedTaskExecutionAction.java:35)
	at org.gradle.execution.BuildOperationFiringBuildWorkerExecutor$ExecuteTasks.call(BuildOperationFiringBuildWorkerExecutor.java:54)
	at org.gradle.execution.BuildOperationFiringBuildWorkerExecutor$ExecuteTasks.call(BuildOperationFiringBuildWorkerExecutor.java:43)
	at org.gradle.internal.operations.DefaultBuildOperationRunner$CallableBuildOperationWorker.execute(DefaultBuildOperationRunner.java:210)
	at org.gradle.internal.operations.DefaultBuildOperationRunner$CallableBuildOperationWorker.execute(DefaultBuildOperationRunner.java:205)
	at org.gradle.internal.operations.DefaultBuildOperationRunner$2.execute(DefaultBuildOperationRunner.java:67)
	at org.gradle.internal.operations.DefaultBuildOperationRunner$2.execute(DefaultBuildOperationRunner.java:60)
	at org.gradle.internal.operations.DefaultBuildOperationRunner.execute(DefaultBuildOperationRunner.java:167)
	at org.gradle.internal.operations.DefaultBuildOperationRunner.execute(DefaultBuildOperationRunner.java:60)
	at org.gradle.internal.operations.DefaultBuildOperationRunner.call(DefaultBuildOperationRunner.java:54)
	at org.gradle.execution.BuildOperationFiringBuildWorkerExecutor.execute(BuildOperationFiringBuildWorkerExecutor.java:40)
	at org.gradle.internal.build.DefaultBuildLifecycleController.lambda$executeTasks$0(DefaultBuildLifecycleController.java:323)
	at org.gradle.internal.model.StateTransitionController.doTransition(StateTransitionController.java:324)
	at org.gradle.internal.model.StateTransitionController.lambda$tryTransition$0(StateTransitionController.java:235)
	at org.gradle.internal.work.DefaultSynchronizer.withLock(DefaultSynchronizer.java:45)
	at org.gradle.internal.model.StateTransitionController.tryTransition(StateTransitionController.java:235)
	at org.gradle.internal.build.DefaultBuildLifecycleController.executeTasks(DefaultBuildLifecycleController.java:314)
	at org.gradle.internal.build.DefaultBuildWorkGraphController$DefaultBuildWorkGraph.runWork(DefaultBuildWorkGraphController.java:220)
	at org.gradle.internal.work.DefaultWorkerLeaseService.lambda$runAndReleaseLocks$0(DefaultWorkerLeaseService.java:302)
	at org.gradle.internal.work.ResourceLockStatistics$1.measure(ResourceLockStatistics.java:43)
	at org.gradle.internal.work.DefaultWorkerLeaseService.runAndReleaseLocks(DefaultWorkerLeaseService.java:300)
	at org.gradle.internal.work.DefaultWorkerLeaseService.withLocksAcquired(DefaultWorkerLeaseService.java:296)
	at org.gradle.internal.work.DefaultWorkerLeaseService.withLocks(DefaultWorkerLeaseService.java:288)
	at org.gradle.internal.work.DefaultWorkerLeaseService.runAsWorkerThread(DefaultWorkerLeaseService.java:131)
	at org.gradle.composite.internal.DefaultBuildController.doRun(DefaultBuildController.java:182)
	at org.gradle.composite.internal.DefaultBuildController$BuildOpRunnable.lambda$run$0(DefaultBuildController.java:199)
	at org.gradle.internal.operations.CurrentBuildOperationRef.with(CurrentBuildOperationRef.java:84)
	at org.gradle.composite.internal.DefaultBuildController$BuildOpRunnable.run(DefaultBuildController.java:199)
	at org.gradle.internal.concurrent.ExecutorPolicy$CatchAndRecordFailures.onExecute(ExecutorPolicy.java:65)
	at org.gradle.internal.concurrent.AbstractManagedExecutor$1.run(AbstractManagedExecutor.java:47)
Caused by: org.gradle.api.internal.artifacts.ivyservice.TypedResolveException: Could not resolve all files for configuration ':app:gplayDebugCompileClasspath'.
	at org.gradle.api.internal.artifacts.ResolveExceptionMapper.mapFailure(ResolveExceptionMapper.java:73)
	at org.gradle.api.internal.artifacts.ResolveExceptionMapper.mapFailures(ResolveExceptionMapper.java:65)
	at org.gradle.api.internal.artifacts.configurations.DefaultConfiguration$DefaultResolutionHost.consolidateFailures(DefaultConfiguration.java:1801)
	at org.gradle.api.internal.artifacts.configurations.ResolutionHost.rethrowFailuresAndReportProblems(ResolutionHost.java:75)
	at org.gradle.api.internal.artifacts.configurations.ResolutionBackedFileCollection.maybeThrowResolutionFailures(ResolutionBackedFileCollection.java:86)
	at org.gradle.api.internal.artifacts.configurations.ResolutionBackedFileCollection.visitContents(ResolutionBackedFileCollection.java:76)
	at org.gradle.api.internal.file.AbstractFileCollection.visitStructure(AbstractFileCollection.java:358)
	at org.gradle.api.internal.file.CompositeFileCollection.lambda$visitContents$0(CompositeFileCollection.java:112)
	at org.gradle.api.internal.file.collections.UnpackingVisitor.add(UnpackingVisitor.java:66)
	at org.gradle.api.internal.file.collections.UnpackingVisitor.add(UnpackingVisitor.java:91)
	at org.gradle.api.internal.file.DefaultFileCollectionFactory$ResolvingFileCollection.visitChildren(DefaultFileCollectionFactory.java:311)
	at org.gradle.api.internal.file.CompositeFileCollection.visitContents(CompositeFileCollection.java:112)
	at org.gradle.api.internal.file.AbstractFileCollection.visitStructure(AbstractFileCollection.java:358)
	at org.gradle.api.internal.file.CompositeFileCollection.lambda$visitContents$0(CompositeFileCollection.java:112)
	at org.gradle.api.internal.tasks.PropertyFileCollection.visitChildren(PropertyFileCollection.java:48)
	at org.gradle.api.internal.file.CompositeFileCollection.visitContents(CompositeFileCollection.java:112)
	at org.gradle.api.internal.file.AbstractFileCollection.visitStructure(AbstractFileCollection.java:358)
	at org.gradle.internal.fingerprint.impl.DefaultFileCollectionSnapshotter.snapshot(DefaultFileCollectionSnapshotter.java:48)
	at org.gradle.internal.execution.impl.DefaultInputFingerprinter$InputCollectingVisitor.visitInputFileProperty(DefaultInputFingerprinter.java:151)
	at org.gradle.api.internal.tasks.execution.TaskExecution.visitMutableInputs(TaskExecution.java:345)
	at org.gradle.internal.execution.impl.DefaultInputFingerprinter.fingerprintInputProperties(DefaultInputFingerprinter.java:77)
	at org.gradle.internal.execution.steps.CaptureMutableStateBeforeExecutionStep.captureExecutionStateWithOutputs(CaptureMutableStateBeforeExecutionStep.java:124)
	at org.gradle.internal.execution.steps.CaptureMutableStateBeforeExecutionStep.lambda$captureExecutionState$0(CaptureMutableStateBeforeExecutionStep.java:94)
	at org.gradle.internal.execution.steps.BuildOperationStep$1.call(BuildOperationStep.java:38)
	at org.gradle.internal.operations.DefaultBuildOperationRunner$CallableBuildOperationWorker.execute(DefaultBuildOperationRunner.java:210)
	at org.gradle.internal.operations.DefaultBuildOperationRunner$CallableBuildOperationWorker.execute(DefaultBuildOperationRunner.java:205)
	at org.gradle.internal.operations.DefaultBuildOperationRunner$2.execute(DefaultBuildOperationRunner.java:67)
	at org.gradle.internal.operations.DefaultBuildOperationRunner$2.execute(DefaultBuildOperationRunner.java:60)
	at org.gradle.internal.operations.DefaultBuildOperationRunner.execute(DefaultBuildOperationRunner.java:167)
	at org.gradle.internal.operations.DefaultBuildOperationRunner.execute(DefaultBuildOperationRunner.java:60)
	at org.gradle.internal.operations.DefaultBuildOperationRunner.call(DefaultBuildOperationRunner.java:54)
	at org.gradle.internal.execution.steps.BuildOperationStep.operation(BuildOperationStep.java:35)
	at org.gradle.internal.execution.steps.CaptureMutableStateBeforeExecutionStep.captureExecutionState(CaptureMutableStateBeforeExecutionStep.java:92)
	at org.gradle.internal.execution.steps.CaptureMutableStateBeforeExecutionStep.executeMutable(CaptureMutableStateBeforeExecutionStep.java:73)
	at org.gradle.internal.execution.steps.CaptureMutableStateBeforeExecutionStep.execute(CaptureMutableStateBeforeExecutionStep.java:65)
	at org.gradle.internal.execution.steps.CaptureMutableStateBeforeExecutionStep.execute(CaptureMutableStateBeforeExecutionStep.java:45)
	at org.gradle.internal.execution.steps.SkipEmptyMutableWorkStep.executeWithNonEmptySources(SkipEmptyMutableWorkStep.java:210)
	at org.gradle.internal.execution.steps.SkipEmptyMutableWorkStep.executeMutable(SkipEmptyMutableWorkStep.java:85)
	at org.gradle.internal.execution.steps.SkipEmptyMutableWorkStep.executeMutable(SkipEmptyMutableWorkStep.java:53)
	at org.gradle.internal.execution.steps.MutableStep.execute(MutableStep.java:26)
	at org.gradle.internal.execution.steps.legacy.MarkSnapshottingInputsStartedStep.execute(MarkSnapshottingInputsStartedStep.java:38)
	at org.gradle.internal.execution.steps.LoadPreviousExecutionStateStep.executeMutable(LoadPreviousExecutionStateStep.java:36)
	at org.gradle.internal.execution.steps.LoadPreviousExecutionStateStep.executeMutable(LoadPreviousExecutionStateStep.java:23)
	at org.gradle.internal.execution.steps.MutableStep.execute(MutableStep.java:26)
	at org.gradle.internal.execution.steps.HandleStaleOutputsStep.executeMutable(HandleStaleOutputsStep.java:77)
	at org.gradle.internal.execution.steps.HandleStaleOutputsStep.executeMutable(HandleStaleOutputsStep.java:43)
	at org.gradle.internal.execution.steps.MutableStep.execute(MutableStep.java:26)
	at org.gradle.internal.execution.steps.AssignMutableWorkspaceStep.lambda$executeMutable$0(AssignMutableWorkspaceStep.java:34)
	at org.gradle.api.internal.tasks.execution.TaskExecution$4.withWorkspace(TaskExecution.java:305)
	at org.gradle.internal.execution.steps.AssignMutableWorkspaceStep.executeMutable(AssignMutableWorkspaceStep.java:30)
	at org.gradle.internal.execution.steps.AssignMutableWorkspaceStep.executeMutable(AssignMutableWorkspaceStep.java:21)
	at org.gradle.internal.execution.steps.MutableStep.execute(MutableStep.java:26)
	at org.gradle.internal.execution.steps.ChoosePipelineStep.execute(ChoosePipelineStep.java:40)
	at org.gradle.internal.execution.steps.ChoosePipelineStep.execute(ChoosePipelineStep.java:23)
	at org.gradle.internal.execution.steps.ExecuteWorkBuildOperationFiringStep.lambda$execute$2(ExecuteWorkBuildOperationFiringStep.java:67)
	at org.gradle.internal.execution.steps.ExecuteWorkBuildOperationFiringStep.execute(ExecuteWorkBuildOperationFiringStep.java:67)
	at org.gradle.internal.execution.steps.ExecuteWorkBuildOperationFiringStep.execute(ExecuteWorkBuildOperationFiringStep.java:39)
	at org.gradle.internal.execution.steps.IdentityCacheStep.execute(IdentityCacheStep.java:46)
	at org.gradle.internal.execution.steps.IdentityCacheStep.execute(IdentityCacheStep.java:34)
	at org.gradle.internal.execution.steps.IdentifyStep.execute(IdentifyStep.java:56)
	at org.gradle.internal.execution.steps.IdentifyStep.execute(IdentifyStep.java:38)
	at org.gradle.internal.execution.impl.DefaultExecutionEngine$1.execute(DefaultExecutionEngine.java:68)
	at org.gradle.api.internal.tasks.execution.ExecuteActionsTaskExecuter.executeIfValid(ExecuteActionsTaskExecuter.java:132)
	at org.gradle.api.internal.tasks.execution.ExecuteActionsTaskExecuter.execute(ExecuteActionsTaskExecuter.java:121)
	at org.gradle.api.internal.tasks.execution.ProblemsTaskPathTrackingTaskExecuter.execute(ProblemsTaskPathTrackingTaskExecuter.java:41)
	at org.gradle.api.internal.tasks.execution.ResolveTaskExecutionModeExecuter.execute(ResolveTaskExecutionModeExecuter.java:51)
	at org.gradle.api.internal.tasks.execution.FinalizePropertiesTaskExecuter.execute(FinalizePropertiesTaskExecuter.java:46)
	at org.gradle.api.internal.tasks.execution.SkipTaskWithNoActionsExecuter.execute(SkipTaskWithNoActionsExecuter.java:57)
	at org.gradle.api.internal.tasks.execution.SkipOnlyIfTaskExecuter.execute(SkipOnlyIfTaskExecuter.java:74)
	at org.gradle.api.internal.tasks.execution.CatchExceptionTaskExecuter.execute(CatchExceptionTaskExecuter.java:36)
	... 54 more
Caused by: org.gradle.internal.resolve.ModuleVersionNotFoundException: Could not find androidx.core:core-telecom:1.1.0-beta01.
Required by:
    project ':app'


Deprecated Gradle features were used in this build, making it incompatible with Gradle 10.

You can use '--warning-mode all' to show the individual deprecation warnings and determine if they come from your own scripts or plugins.

For more on this, please refer to https://docs.gradle.org/9.7.1/userguide/command_line_interface.html#sec:command_line_warnings in the Gradle documentation.

BUILD FAILED in 2m 2s
1 actionable task: 1 executed
```
