# Android Auto foundation build

Result: **FAIL**

```text
	at org.gradle.internal.serialize.graph.RunningKt.runToCompletion(Running.kt:58)
	at org.gradle.internal.serialize.graph.RunningKt.runWriteOperation(Running.kt:42)
	at org.gradle.internal.serialize.graph.CodecKt.writeWith(Codec.kt:83)
	at org.gradle.internal.cc.impl.DefaultConfigurationCacheIO.withWriteContextFor(DefaultConfigurationCacheIO.kt:521)
	at org.gradle.internal.cc.impl.ConfigurationCacheBuildTreeIO.withWriteContextFor(ConfigurationCacheBuildTreeIO.kt:131)
	at org.gradle.internal.cc.impl.DefaultConfigurationCacheIO.withWriteContextFor(DefaultConfigurationCacheIO.kt:101)
	at org.gradle.internal.cc.impl.ConfigurationCacheBuildTreeIO.withWriteContextFor$default(ConfigurationCacheBuildTreeIO.kt:124)
	at org.gradle.internal.cc.impl.DefaultConfigurationCacheIO.writeConfigurationCacheStateWithSpecialEncoders(DefaultConfigurationCacheIO.kt:368)
	at org.gradle.internal.cc.impl.DefaultConfigurationCacheIO.writeConfigurationCacheState$lambda$0$0(DefaultConfigurationCacheIO.kt:272)
	at org.gradle.internal.cc.impl.DefaultConfigurationCacheIO.withSharedObjectEncoderFor(DefaultConfigurationCacheIO.kt:331)
	at org.gradle.internal.cc.impl.DefaultConfigurationCacheIO.writeConfigurationCacheState$lambda$0(DefaultConfigurationCacheIO.kt:271)
	at org.gradle.internal.cc.impl.DefaultConfigurationCacheIO.withStringEncoderFor(DefaultConfigurationCacheIO.kt:319)
	at org.gradle.internal.cc.impl.DefaultConfigurationCacheIO.writeConfigurationCacheState(DefaultConfigurationCacheIO.kt:270)
	at org.gradle.internal.cc.impl.DefaultConfigurationCacheIO.writeRootBuildStateTo(DefaultConfigurationCacheIO.kt:218)
	at org.gradle.internal.cc.impl.DefaultConfigurationCache.writeConfigurationCacheState(DefaultConfigurationCache.kt:803)
	at org.gradle.internal.cc.impl.DefaultConfigurationCache.saveWorkGraph$lambda$0$0(DefaultConfigurationCache.kt:717)
	at org.gradle.internal.cc.impl.DefaultConfigurationCache.runAndStore$lambda$0(DefaultConfigurationCache.kt:732)
	at org.gradle.internal.cc.impl.ConfigurationCacheRepository$StoreImpl.useForStore$lambda$0(ConfigurationCacheRepository.kt:255)
	at org.gradle.internal.cc.impl.ConfigurationCacheRepository$withExclusiveAccessToCache$1.get(ConfigurationCacheRepository.kt:321)
	at org.gradle.cache.internal.LockOnDemandCrossProcessCacheAccess.withFileLock(LockOnDemandCrossProcessCacheAccess.java:90)
	at org.gradle.cache.internal.DefaultCacheCoordinator.withFileLock(DefaultCacheCoordinator.java:226)
	at org.gradle.cache.internal.DefaultPersistentDirectoryStore.withFileLock(DefaultPersistentDirectoryStore.java:148)
	at org.gradle.cache.internal.DefaultCacheFactory$ReferenceTrackingPersistentCache.withFileLock(DefaultCacheFactory.java:245)
	at org.gradle.internal.cc.impl.ConfigurationCacheRepository.withExclusiveAccessToCache(ConfigurationCacheRepository.kt:319)
	at org.gradle.internal.cc.impl.ConfigurationCacheRepository.access$withExclusiveAccessToCache(ConfigurationCacheRepository.kt:54)
	at org.gradle.internal.cc.impl.ConfigurationCacheRepository$StoreImpl.useForStore(ConfigurationCacheRepository.kt:245)
	at org.gradle.internal.cc.impl.DefaultConfigurationCache.runAndStore(DefaultConfigurationCache.kt:729)
	at org.gradle.internal.cc.impl.DefaultConfigurationCache.saveWorkGraph$lambda$0(DefaultConfigurationCache.kt:716)
	at org.gradle.internal.cc.operations.ConfigurationCacheBuildOperationsKt$withWorkGraphStoreOperation$1.run(ConfigurationCacheBuildOperations.kt:63)
	at org.gradle.internal.operations.DefaultBuildOperationRunner$1.execute(DefaultBuildOperationRunner.java:30)
	at org.gradle.internal.operations.DefaultBuildOperationRunner$1.execute(DefaultBuildOperationRunner.java:27)
	at org.gradle.internal.operations.DefaultBuildOperationRunner$2.execute(DefaultBuildOperationRunner.java:67)
	at org.gradle.internal.operations.DefaultBuildOperationRunner$2.execute(DefaultBuildOperationRunner.java:60)
	at org.gradle.internal.operations.DefaultBuildOperationRunner.execute(DefaultBuildOperationRunner.java:167)
	at org.gradle.internal.operations.DefaultBuildOperationRunner.execute(DefaultBuildOperationRunner.java:60)
	at org.gradle.internal.operations.DefaultBuildOperationRunner.run(DefaultBuildOperationRunner.java:48)
	at org.gradle.internal.cc.operations.ConfigurationCacheBuildOperationsKt.withWorkGraphStoreOperation(ConfigurationCacheBuildOperations.kt:56)
	at org.gradle.internal.cc.impl.DefaultConfigurationCache.saveWorkGraph(DefaultConfigurationCache.kt:715)
	at org.gradle.internal.cc.impl.DefaultConfigurationCache.loadOrScheduleRequestedTasks$lambda$1$0(DefaultConfigurationCache.kt:282)
	at org.gradle.internal.cc.impl.DefaultConfigurationCache.degradeGracefullyOr(DefaultConfigurationCache.kt:342)
	at org.gradle.internal.cc.impl.DefaultConfigurationCache.loadOrScheduleRequestedTasks$lambda$1(DefaultConfigurationCache.kt:282)
	at org.gradle.internal.cc.impl.DefaultConfigurationCache.runWorkThatContributesToCacheEntry(DefaultConfigurationCache.kt:654)
	at org.gradle.internal.cc.impl.DefaultConfigurationCache.loadOrScheduleRequestedTasks(DefaultConfigurationCache.kt:279)
	at org.gradle.internal.cc.impl.ConfigurationCacheAwareBuildTreeWorkController$scheduleAndRunRequestedTasks$executionResult$1$result$1.call(ConfigurationCacheAwareBuildTreeWorkController.kt:57)
	at org.gradle.internal.cc.impl.ConfigurationCacheAwareBuildTreeWorkController$scheduleAndRunRequestedTasks$executionResult$1$result$1.call(ConfigurationCacheAwareBuildTreeWorkController.kt:56)
	at org.gradle.internal.Try.ofFailable(Try.java:46)
	at org.gradle.internal.cc.impl.ConfigurationCacheAwareBuildTreeWorkController$scheduleAndRunRequestedTasks$executionResult$1.apply(ConfigurationCacheAwareBuildTreeWorkController.kt:56)
	at org.gradle.internal.cc.impl.ConfigurationCacheAwareBuildTreeWorkController$scheduleAndRunRequestedTasks$executionResult$1.apply(ConfigurationCacheAwareBuildTreeWorkController.kt:55)
	at org.gradle.composite.internal.DefaultIncludedBuildTaskGraph.withNewWorkGraph(DefaultIncludedBuildTaskGraph.java:115)
	at org.gradle.internal.cc.impl.ConfigurationCacheAwareBuildTreeWorkController.scheduleAndRunRequestedTasks(ConfigurationCacheAwareBuildTreeWorkController.kt:55)
	at org.gradle.internal.buildtree.DefaultBuildTreeLifecycleController.lambda$scheduleAndRunTasks$0(DefaultBuildTreeLifecycleController.java:80)
	at org.gradle.internal.buildtree.DefaultBuildTreeLifecycleController.lambda$runBuild$0(DefaultBuildTreeLifecycleController.java:166)
	at org.gradle.internal.model.StateTransitionController.lambda$transition$2(StateTransitionController.java:227)
	at org.gradle.internal.model.StateTransitionController.doTransition(StateTransitionController.java:324)
	at org.gradle.internal.model.StateTransitionController.lambda$transition$1(StateTransitionController.java:227)
	at org.gradle.internal.work.DefaultSynchronizer.withLock(DefaultSynchronizer.java:45)
	at org.gradle.internal.model.StateTransitionController.transition(StateTransitionController.java:227)
	at org.gradle.internal.buildtree.DefaultBuildTreeLifecycleController.runBuild(DefaultBuildTreeLifecycleController.java:163)
	at org.gradle.internal.buildtree.DefaultBuildTreeLifecycleController.scheduleAndRunTasks(DefaultBuildTreeLifecycleController.java:80)
	at org.gradle.internal.buildtree.DefaultBuildTreeLifecycleController.scheduleAndRunTasks(DefaultBuildTreeLifecycleController.java:75)
	at org.gradle.tooling.internal.provider.ExecuteBuildActionRunner.run(ExecuteBuildActionRunner.java:31)
	at org.gradle.launcher.exec.ChainingBuildActionRunner.run(ChainingBuildActionRunner.java:35)
	at org.gradle.internal.buildtree.ProblemReportingBuildActionRunner.run(ProblemReportingBuildActionRunner.java:55)
	at org.gradle.launcher.exec.BuildOutcomeReportingBuildActionRunner.run(BuildOutcomeReportingBuildActionRunner.java:83)
	at org.gradle.tooling.internal.provider.FileSystemWatchingBuildActionRunner.run(FileSystemWatchingBuildActionRunner.java:118)
	at org.gradle.launcher.exec.BuildCompletionNotifyingBuildActionRunner.run(BuildCompletionNotifyingBuildActionRunner.java:64)
	at org.gradle.launcher.exec.RootBuildLifecycleBuildActionExecutor.lambda$execute$0(RootBuildLifecycleBuildActionExecutor.java:97)
	at org.gradle.composite.internal.DefaultRootBuildState.run(DefaultRootBuildState.java:119)
	at org.gradle.launcher.exec.RootBuildLifecycleBuildActionExecutor.execute(RootBuildLifecycleBuildActionExecutor.java:97)
	at org.gradle.launcher.exec.DefaultBuildTreeActionExecutor.runBuildTreeLifecycle(DefaultBuildTreeActionExecutor.java:126)
	at org.gradle.launcher.exec.DefaultBuildTreeActionExecutor.access$100(DefaultBuildTreeActionExecutor.java:52)
	at org.gradle.launcher.exec.DefaultBuildTreeActionExecutor$2.call(DefaultBuildTreeActionExecutor.java:98)
	at org.gradle.launcher.exec.DefaultBuildTreeActionExecutor$2.call(DefaultBuildTreeActionExecutor.java:94)
	at org.gradle.internal.operations.DefaultBuildOperationRunner$CallableBuildOperationWorker.execute(DefaultBuildOperationRunner.java:210)
	at org.gradle.internal.operations.DefaultBuildOperationRunner$CallableBuildOperationWorker.execute(DefaultBuildOperationRunner.java:205)
	at org.gradle.internal.operations.DefaultBuildOperationRunner$2.execute(DefaultBuildOperationRunner.java:67)
	at org.gradle.internal.operations.DefaultBuildOperationRunner$2.execute(DefaultBuildOperationRunner.java:60)
	at org.gradle.internal.operations.DefaultBuildOperationRunner.execute(DefaultBuildOperationRunner.java:167)
	at org.gradle.internal.operations.DefaultBuildOperationRunner.execute(DefaultBuildOperationRunner.java:60)
	at org.gradle.internal.operations.DefaultBuildOperationRunner.call(DefaultBuildOperationRunner.java:54)
	at org.gradle.launcher.exec.DefaultBuildTreeActionExecutor.runAsBuildOperation(DefaultBuildTreeActionExecutor.java:94)
	at org.gradle.launcher.exec.DefaultBuildTreeActionExecutor.lambda$runBuildTreeAction$0(DefaultBuildTreeActionExecutor.java:88)
	at org.gradle.internal.work.DefaultWorkerLeaseService.lambda$runAndReleaseLocks$0(DefaultWorkerLeaseService.java:302)
	at org.gradle.internal.work.ResourceLockStatistics$1.measure(ResourceLockStatistics.java:43)
	at org.gradle.internal.work.DefaultWorkerLeaseService.runAndReleaseLocks(DefaultWorkerLeaseService.java:300)
	at org.gradle.internal.work.DefaultWorkerLeaseService.withLocksAcquired(DefaultWorkerLeaseService.java:296)
	at org.gradle.internal.work.DefaultWorkerLeaseService.withLocks(DefaultWorkerLeaseService.java:288)
	at org.gradle.internal.work.DefaultWorkerLeaseService.runAsWorkerThread(DefaultWorkerLeaseService.java:131)
	at org.gradle.launcher.exec.DefaultBuildTreeActionExecutor.runBuildTreeAction(DefaultBuildTreeActionExecutor.java:88)
	at org.gradle.tooling.internal.provider.continuous.ContinuousBuildActionExecutor.execute(ContinuousBuildActionExecutor.java:111)
	at org.gradle.tooling.internal.provider.SubscribableBuildActionExecutor.execute(SubscribableBuildActionExecutor.java:64)
	at org.gradle.internal.session.DefaultBuildSessionContext.execute(DefaultBuildSessionContext.java:46)
	at org.gradle.internal.buildprocess.execution.BuildSessionLifecycleBuildActionExecutor$ActionImpl.apply(BuildSessionLifecycleBuildActionExecutor.java:106)
	at org.gradle.internal.buildprocess.execution.BuildSessionLifecycleBuildActionExecutor$ActionImpl.apply(BuildSessionLifecycleBuildActionExecutor.java:94)
	at org.gradle.internal.session.BuildSessionState.run(BuildSessionState.java:73)
	at org.gradle.internal.buildprocess.execution.BuildSessionLifecycleBuildActionExecutor.execute(BuildSessionLifecycleBuildActionExecutor.java:67)
	at org.gradle.internal.buildprocess.execution.BuildSessionLifecycleBuildActionExecutor.execute(BuildSessionLifecycleBuildActionExecutor.java:45)
	at org.gradle.internal.buildprocess.execution.StartParamsValidatingActionExecutor.execute(StartParamsValidatingActionExecutor.java:57)
	at org.gradle.internal.buildprocess.execution.StartParamsValidatingActionExecutor.execute(StartParamsValidatingActionExecutor.java:32)
	at org.gradle.internal.buildprocess.execution.SessionFailureReportingActionExecutor.execute(SessionFailureReportingActionExecutor.java:51)
	at org.gradle.internal.buildprocess.execution.SessionFailureReportingActionExecutor.execute(SessionFailureReportingActionExecutor.java:39)
	at org.gradle.internal.buildprocess.execution.SetupLoggingActionExecutor.execute(SetupLoggingActionExecutor.java:47)
	at org.gradle.internal.buildprocess.execution.SetupLoggingActionExecutor.execute(SetupLoggingActionExecutor.java:31)
	at org.gradle.launcher.daemon.server.exec.ExecuteBuild.doBuild(ExecuteBuild.java:70)
	at org.gradle.launcher.daemon.server.exec.BuildCommandOnly.execute(BuildCommandOnly.java:37)
	at org.gradle.launcher.daemon.server.api.DaemonCommandExecution.proceed(DaemonCommandExecution.java:104)
	at org.gradle.launcher.daemon.server.exec.WatchForDisconnection.execute(WatchForDisconnection.java:39)
	at org.gradle.launcher.daemon.server.api.DaemonCommandExecution.proceed(DaemonCommandExecution.java:104)
	at org.gradle.launcher.daemon.server.exec.ResetDeprecationLogger.execute(ResetDeprecationLogger.java:29)
	at org.gradle.launcher.daemon.server.api.DaemonCommandExecution.proceed(DaemonCommandExecution.java:104)
	at org.gradle.launcher.daemon.server.exec.RequestStopIfSingleUsedDaemon.execute(RequestStopIfSingleUsedDaemon.java:35)
	at org.gradle.launcher.daemon.server.api.DaemonCommandExecution.proceed(DaemonCommandExecution.java:104)
	at org.gradle.launcher.daemon.server.exec.ForwardClientInput.lambda$execute$0(ForwardClientInput.java:40)
	at org.gradle.internal.daemon.clientinput.ClientInputForwarder.forwardInput(ClientInputForwarder.java:80)
	at org.gradle.launcher.daemon.server.exec.ForwardClientInput.execute(ForwardClientInput.java:37)
	at org.gradle.launcher.daemon.server.api.DaemonCommandExecution.proceed(DaemonCommandExecution.java:104)
	at org.gradle.launcher.daemon.server.exec.LogAndCheckHealth.execute(LogAndCheckHealth.java:64)
	at org.gradle.launcher.daemon.server.api.DaemonCommandExecution.proceed(DaemonCommandExecution.java:104)
	at org.gradle.launcher.daemon.server.exec.ApplyClientEnvironmentVariables.doBuild(ApplyClientEnvironmentVariables.java:80)
	at org.gradle.launcher.daemon.server.exec.BuildCommandOnly.execute(BuildCommandOnly.java:37)
	at org.gradle.launcher.daemon.server.api.DaemonCommandExecution.proceed(DaemonCommandExecution.java:104)
	at org.gradle.launcher.daemon.server.exec.LogToClient.doBuild(LogToClient.java:63)
	at org.gradle.launcher.daemon.server.exec.BuildCommandOnly.execute(BuildCommandOnly.java:37)
	at org.gradle.launcher.daemon.server.api.DaemonCommandExecution.proceed(DaemonCommandExecution.java:104)
	at org.gradle.launcher.daemon.server.exec.EstablishBuildEnvironment.doBuild(EstablishBuildEnvironment.java:74)
	at org.gradle.launcher.daemon.server.exec.BuildCommandOnly.execute(BuildCommandOnly.java:37)
	at org.gradle.launcher.daemon.server.api.DaemonCommandExecution.proceed(DaemonCommandExecution.java:104)
	at org.gradle.launcher.daemon.server.exec.StartBuildOrRespondWithBusy$1.run(StartBuildOrRespondWithBusy.java:52)
	at org.gradle.launcher.daemon.server.DaemonStateCoordinator.lambda$runCommand$0(DaemonStateCoordinator.java:321)
	at org.gradle.internal.concurrent.ExecutorPolicy$CatchAndRecordFailures.onExecute(ExecutorPolicy.java:65)
	at org.gradle.internal.concurrent.AbstractManagedExecutor$1.run(AbstractManagedExecutor.java:47)
Caused by: org.gradle.api.internal.artifacts.verification.exceptions.DependencyVerificationException: Dependency verification failed for configuration ':app:gplayDebugCompileClasspath':
  - On artifact app-1.7.0.module (androidx.car.app:app:1.7.0) in repository 'Google': Artifact was signed with key '0F06FF86BEEAF4E71866EE5232EE5355A6BC6E42' and passed verification but the key isn't in your trusted keys list.
  - On artifact app-projected-1.7.0.module (androidx.car.app:app-projected:1.7.0) in repository 'Google': Artifact was signed with key '0F06FF86BEEAF4E71866EE5232EE5355A6BC6E42' and passed verification but the key isn't in your trusted keys list.
  - On artifact activity-1.2.0.module (androidx.activity:activity:1.2.0) in repository 'Google': checksum is missing from verification metadata.
  - On artifact core-1.7.0.module (androidx.core:core:1.7.0) in repository 'Google': checksum is missing from verification metadata.
  - On artifact lifecycle-common-java8-2.2.0.pom (androidx.lifecycle:lifecycle-common-java8:2.2.0) in repository 'Google': checksum is missing from verification metadata.
  - On artifact lifecycle-viewmodel-2.2.0.pom (androidx.lifecycle:lifecycle-viewmodel:2.2.0) in repository 'Google': checksum is missing from verification metadata.

If the artifacts are trustworthy, you will need to update the gradle/verification-metadata.xml file. For more on how to do this, please refer to https://docs.gradle.org/9.7.1/userguide/dependency_verification.html#sec:troubleshooting-verification in the Gradle documentation.

These files failed verification:
  - GRADLE_USER_HOME/caches/modules-2/files-2.1/androidx.activity/activity/1.2.0/bf16f90bedd7c7a07e49b50855f054d7ce6d334/activity-1.2.0.module
  - GRADLE_USER_HOME/caches/modules-2/files-2.1/androidx.car.app/app-projected/1.7.0/2a6571ed3083306a86b878832a46e65071b90321/app-projected-1.7.0.module (signature: GRADLE_USER_HOME/caches/modules-2/files-2.1/androidx.car.app/app-projected/1.7.0/fba5cd3d1ac1d4ca2396e2baf3d6fed27b18a0dd/app-projected-1.7.0.module.asc)
  - GRADLE_USER_HOME/caches/modules-2/files-2.1/androidx.car.app/app/1.7.0/8ab21571b692c6c17f0671e2ee6505f3010b41d9/app-1.7.0.module (signature: GRADLE_USER_HOME/caches/modules-2/files-2.1/androidx.car.app/app/1.7.0/5cdaf21b75e7acc9c208da5d57ddec72a88b6064/app-1.7.0.module.asc)
  - GRADLE_USER_HOME/caches/modules-2/files-2.1/androidx.core/core/1.7.0/2a8d5bf97abc192d56c1b610479c4d82ce88f086/core-1.7.0.module
  - GRADLE_USER_HOME/caches/modules-2/files-2.1/androidx.lifecycle/lifecycle-common-java8/2.2.0/873ea8000ab8cb7c7eb7e0a8234028bfbc16e613/lifecycle-common-java8-2.2.0.pom
  - GRADLE_USER_HOME/caches/modules-2/files-2.1/androidx.lifecycle/lifecycle-viewmodel/2.2.0/5b85f9eedca6f1e45f62c6d3ca150cae66acf366/lifecycle-viewmodel-2.2.0.pom

GRADLE_USER_HOME = /home/runner/.gradle

These files failed verification:
  - GRADLE_USER_HOME/caches/modules-2/files-2.1/androidx.activity/activity/1.2.0/bf16f90bedd7c7a07e49b50855f054d7ce6d334/activity-1.2.0.module
  - GRADLE_USER_HOME/caches/modules-2/files-2.1/androidx.car.app/app-projected/1.7.0/2a6571ed3083306a86b878832a46e65071b90321/app-projected-1.7.0.module (signature: GRADLE_USER_HOME/caches/modules-2/files-2.1/androidx.car.app/app-projected/1.7.0/fba5cd3d1ac1d4ca2396e2baf3d6fed27b18a0dd/app-projected-1.7.0.module.asc)
  - GRADLE_USER_HOME/caches/modules-2/files-2.1/androidx.car.app/app/1.7.0/8ab21571b692c6c17f0671e2ee6505f3010b41d9/app-1.7.0.module (signature: GRADLE_USER_HOME/caches/modules-2/files-2.1/androidx.car.app/app/1.7.0/5cdaf21b75e7acc9c208da5d57ddec72a88b6064/app-1.7.0.module.asc)
  - GRADLE_USER_HOME/caches/modules-2/files-2.1/androidx.core/core/1.7.0/2a8d5bf97abc192d56c1b610479c4d82ce88f086/core-1.7.0.module
  - GRADLE_USER_HOME/caches/modules-2/files-2.1/androidx.lifecycle/lifecycle-common-java8/2.2.0/873ea8000ab8cb7c7eb7e0a8234028bfbc16e613/lifecycle-common-java8-2.2.0.pom
  - GRADLE_USER_HOME/caches/modules-2/files-2.1/androidx.lifecycle/lifecycle-viewmodel/2.2.0/5b85f9eedca6f1e45f62c6d3ca150cae66acf366/lifecycle-viewmodel-2.2.0.pom

GRADLE_USER_HOME = /home/runner/.gradle

Open this report for more details: file:///home/runner/work/Nextcloud_Talk_Android_Auto/Nextcloud_Talk_Android_Auto/build/reports/dependency-verification/at-1787598367480/dependency-verification-report.html
	at org.gradle.api.internal.artifacts.ivyservice.ivyresolve.verification.ChecksumAndSignatureVerificationOverride.artifactsAccessed(ChecksumAndSignatureVerificationOverride.java:196)
	at org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ResolvedArtifactSetResolver$1.run(ResolvedArtifactSetResolver.java:69)
	at org.gradle.internal.operations.DefaultBuildOperationRunner$1.execute(DefaultBuildOperationRunner.java:30)
	at org.gradle.internal.operations.DefaultBuildOperationRunner$1.execute(DefaultBuildOperationRunner.java:27)
	at org.gradle.internal.operations.DefaultBuildOperationRunner$2.execute(DefaultBuildOperationRunner.java:67)
	at org.gradle.internal.operations.DefaultBuildOperationRunner$2.execute(DefaultBuildOperationRunner.java:60)
	at org.gradle.internal.operations.DefaultBuildOperationRunner.execute(DefaultBuildOperationRunner.java:167)
	at org.gradle.internal.operations.DefaultBuildOperationRunner.execute(DefaultBuildOperationRunner.java:60)
	at org.gradle.internal.operations.DefaultBuildOperationRunner.run(DefaultBuildOperationRunner.java:48)
	at org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ResolvedArtifactSetResolver.visitArtifacts(ResolvedArtifactSetResolver.java:65)
	at org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ResolvedArtifactSetResolver.lambda$visitInUnmanagedWorkerThread$0(ResolvedArtifactSetResolver.java:61)
	at org.gradle.internal.work.DefaultWorkerLeaseService.runAsUnmanagedWorkerThread(DefaultWorkerLeaseService.java:159)
	at org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ResolvedArtifactSetResolver.visitInUnmanagedWorkerThread(ResolvedArtifactSetResolver.java:61)
	at org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.DefaultSelectedArtifactSet.visitArtifacts(DefaultSelectedArtifactSet.java:59)
	at org.gradle.api.internal.artifacts.configurations.ResolutionResultProviderBackedSelectedArtifactSet.visitArtifacts(ResolutionResultProviderBackedSelectedArtifactSet.java:52)
	at org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.SelectedArtifactSet.visitFiles(SelectedArtifactSet.java:34)
	at org.gradle.api.internal.artifacts.configurations.ResolutionBackedFileCollection.visitContents(ResolutionBackedFileCollection.java:75)
	at org.gradle.api.internal.file.AbstractFileCollection.visitStructure(AbstractFileCollection.java:358)
	at org.gradle.internal.serialize.codecs.core.CollectingVisitor.startVisit(FileCollectionCodec.kt:208)
	at org.gradle.api.internal.file.AbstractFileCollection.visitStructure(AbstractFileCollection.java:357)
	at org.gradle.internal.serialize.codecs.core.FileCollectionCodec.encodeViaCollectingVisitor(FileCollectionCodec.kt:81)
	at org.gradle.internal.serialize.codecs.core.FileCollectionCodec.encodeContents(FileCollectionCodec.kt:74)
	at org.gradle.internal.serialize.codecs.core.FileCollectionCodec.encode(FileCollectionCodec.kt:63)
	at org.gradle.internal.serialize.codecs.core.FileCollectionCodec.encode(FileCollectionCodec.kt:55)
	at org.gradle.internal.serialize.graph.codecs.BindingsBackedCodec.encode(BindingsBackedCodec.kt:66)
	at org.gradle.internal.serialize.graph.DefaultWriteContext.write(Contexts.kt:111)
	at org.gradle.internal.serialize.graph.BeanPropertyExtensionsKt.writePropertyValue(BeanPropertyExtensions.kt:34)
	... 230 more


Deprecated Gradle features were used in this build, making it incompatible with Gradle 10.

You can use '--warning-mode all' to show the individual deprecation warnings and determine if they come from your own scripts or plugins.

For more on this, please refer to https://docs.gradle.org/9.7.1/userguide/command_line_interface.html#sec:command_line_warnings in the Gradle documentation.

BUILD FAILED in 1m 47s
Configuration cache entry discarded due to serialization error.
```
