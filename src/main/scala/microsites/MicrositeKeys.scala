/*
 * Copyright 2016-2017 47 Degrees, LLC. <http://www.47deg.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package microsites

import com.typesafe.sbt.sbtghpages.GhpagesPlugin.autoImport.{
  ghpagesBranch,
  ghpagesNoJekyll,
  ghpagesPushSite
}
import com.typesafe.sbt.site.SitePlugin.autoImport.{makeSite, siteDirectory}
import com.typesafe.sbt.site.jekyll.JekyllPlugin.autoImport._
import microsites.util.MicrositeHelper
import sbt.Keys._
import sbt._
import sbt.complete.DefaultParsers.OptNotSpace
import sbtorgpolicies.github.GitHubOps
import tut.TutPlugin.autoImport._

trait MicrositeKeys {

  sealed abstract class GitHostingService(val name: String) extends Product with Serializable
  final case object GitHub                                  extends GitHostingService("GitHub")
  final case object GitLab                                  extends GitHostingService("GitLab")
  final case object Bitbucket                               extends GitHostingService("Bitbucket")
  final case class Other(value: String)                     extends GitHostingService(value)

  sealed abstract class PushWith(val name: String) extends Product with Serializable
  final case object GHPagesPlugin                  extends PushWith("ghPagesPlugin")
  final case object GitHub4s                       extends PushWith("github4s")

  object GitHostingService {
    implicit def string2GitHostingService(name: String): GitHostingService = {
      List(GitHub, GitLab, Bitbucket)
        .find(_.name.toLowerCase == name.toLowerCase)
        .getOrElse(Other(name))
    }
  }

  val makeMicrosite: TaskKey[Unit] = taskKey[Unit]("Main Task to build a Microsite")
  val publishMicrosite: TaskKey[Unit] =
    taskKey[Unit]("Task helper that wraps the `publishMicrositeCommand`.")
  val microsite: TaskKey[Seq[File]] = taskKey[Seq[File]]("Create microsite files")
  val micrositeConfig: TaskKey[Unit] =
    taskKey[Unit]("Copy microsite config to the site folder")
  val micrositeMakeExtraMdFiles: TaskKey[File] =
    taskKey[File]("Create microsite extra md files")
  val micrositeTutExtraMdFiles: TaskKey[Seq[File]] =
    taskKey[Seq[File]]("Run tut for extra microsite md files")
  val micrositeName: SettingKey[String]        = settingKey[String]("Microsite name")
  val micrositeDescription: SettingKey[String] = settingKey[String]("Microsite description")
  val micrositeAuthor: SettingKey[String]      = settingKey[String]("Microsite author")
  val micrositeHomepage: SettingKey[String]    = settingKey[String]("Microsite homepage")
  val micrositeOrganizationHomepage: SettingKey[String] =
    settingKey[String]("Microsite organisation homepage")
  val micrositeTwitter: SettingKey[String]        = settingKey[String]("Microsite twitter")
  val micrositeTwitterCreator: SettingKey[String] = settingKey[String]("Microsite twitter")
  val micrositeBaseUrl: SettingKey[String]        = settingKey[String]("Microsite site base url")
  val micrositeDocumentationUrl: SettingKey[String] =
    settingKey[String]("Microsite site documentation url")
  val micrositeHighlightTheme: SettingKey[String] = settingKey[String]("Microsite Highlight Theme")
  val micrositeConfigYaml: SettingKey[ConfigYml] =
    settingKey[ConfigYml]("Microsite _config.yml file configuration.")
  val micrositeImgDirectory: SettingKey[File] = settingKey[File](
    "Optional. Microsite images directory. By default, it'll be the resourcesDirectory + '/microsite/img'")
  val micrositeCssDirectory: SettingKey[File] = settingKey[File](
    "Optional. Microsite CSS directory. By default, it'll be the resourcesDirectory + '/microsite/css'")
  val micrositeJsDirectory: SettingKey[File] = settingKey[File](
    "Optional. Microsite Javascript directory. By default, it'll be the resourcesDirectory + '/microsite/js'")
  val micrositeCDNDirectives: SettingKey[CdnDirectives] = settingKey[CdnDirectives](
    "Optional. Microsite CDN directives lists (for css and js imports). By default, both lists are empty.")
  val micrositeExternalLayoutsDirectory: SettingKey[File] = settingKey[File](
    "Optional. Microsite external layouts directory. By default, it'll be the resourcesDirectory + '/microsite/layout'")
  val micrositeExternalIncludesDirectory: SettingKey[File] = settingKey[File](
    "Optional. Microsite external includes (partial layouts) directory. By default, it'll be the resourcesDirectory + '/microsite/includes'")
  val micrositeDataDirectory: SettingKey[File] = settingKey[File](
    "Optional. Microsite Data directory, useful to define the microsite data files " +
      "(https://jekyllrb.com/docs/datafiles/). By default, it'll be the resourcesDirectory + '/microsite/data'")
  val micrositeExtraMdFiles: SettingKey[Map[File, ExtraMdFileConfig]] =
    settingKey[Map[File, ExtraMdFileConfig]](
      "Optional. This key is useful when you want to include automatically markdown documents as a part of your microsite, and these files are located in different places from the tutSourceDirectory. The map key is related with the source file, the map value corresponds with the target relative file path and the document meta-information configuration. By default, the map is empty.")
  val micrositeExtraMdFilesOutput: SettingKey[File] = settingKey[File](
    "Optional. Microsite output location for extra-md files. Default is resourceManaged + '/jekyll/_extra_md'")
  val micrositePalette: SettingKey[Map[String, String]] =
    settingKey[Map[String, String]]("Microsite palette")
  val micrositeFavicons: SettingKey[Seq[MicrositeFavicon]] = settingKey[Seq[MicrositeFavicon]](
    "Optional. List of filenames and sizes for the PNG/ICO files to be used as favicon for the generated site, located in '/microsite/img'. The sizes should be described with a string (i.e.: \"16x16\"). By default, favicons with different sizes will be generated from the navbar_brand2x.jpg file.")
  val micrositeGithubOwner: SettingKey[String] = settingKey[String]("Microsite Github owner")
  val micrositeGithubRepo: SettingKey[String]  = settingKey[String]("Microsite Github repo")
  val micrositeGithubToken: SettingKey[Option[String]] =
    settingKey[Option[String]]("Microsite Github token for pushing the microsite")
  val micrositeKazariEvaluatorUrl: SettingKey[String] = settingKey[String](
    "URL of the remote Scala Evaluator to be used by Kazari. Required for Kazari to work. Default: https://scala-evaluator-212.herokuapp.com")
  val micrositeKazariEvaluatorToken: SettingKey[String] = settingKey[String](
    "Remote Scala Evaluator token to be used by Kazari. Required for Kazari to work. Default: token compatible with the Scala Exercises remote evaluator.")
  val micrositeKazariGithubToken: SettingKey[String] = settingKey[String](
    "GitHub token to be used by Kazari. Required for Kazari to perform certain actions (i.e. save Gists). Default: empty string")
  val micrositeKazariCodeMirrorTheme: SettingKey[String] = settingKey[String](
    "Optional. CodeMirror theme to be used by Kazari in its modal editor. Default: monokai")
  val micrositeKazariDependencies: SettingKey[Seq[KazariDependency]] =
    settingKey[Seq[KazariDependency]](
      "Optional. List of dependencies needed to compile the code to be evaluated by Kazari (set of groupId, artifactId, scalaVersion and versionId). Default: empty list")
  val micrositeKazariResolvers: SettingKey[Seq[String]] = settingKey[Seq[String]](
    "Optional. List of resolver urls needed for the provided dependencies to be fetched by Kazari. Default: empty list")
  val micrositeGitHostingService: SettingKey[GitHostingService] =
    settingKey[GitHostingService]("Service used for git hosting. By default, it'll be GitHub.")
  val micrositeGitHostingUrl: SettingKey[String] = settingKey[String](
    "In the case where your project isn't hosted on Github, use this setting to point users to git host (e.g. 'https://internal.gitlab.com/<user>/<project>').")
  val micrositePushSiteWith: SettingKey[PushWith] =
    settingKey[PushWith](
      "Determines what will be chosen for pushing the site. The options are sbt-ghpages plugin and github4s library.")

  val micrositeAnalyticsToken: SettingKey[String] =
    settingKey[String](
      "Optional. Add your property id of Google Analytics to add a Google Analytics tracker")
  val micrositeGitterChannel: SettingKey[Boolean] = SettingKey[Boolean](
    "Optional. Includes Gitter sidecar Chat functionality. Enabled by default."
  )
  val micrositeGitterChannelUrl: SettingKey[String] = SettingKey[String](
    "Optional. Add custom Gitter sidecar Chat URL. By default is owner/repository."
  )

  val publishMicrositeCommandKey: String = "publishMicrositeCommand"
}

object MicrositeKeys extends MicrositeKeys

trait MicrositeAutoImportSettings extends MicrositeKeys {

  lazy val micrositeHelper: Def.Initialize[MicrositeHelper] = Def.setting {
    val baseUrl =
      if (!micrositeBaseUrl.value.isEmpty && !micrositeBaseUrl.value.startsWith("/"))
        s"/${micrositeBaseUrl.value}"
      else micrositeBaseUrl.value

    val defaultYamlCustomVariables = Map(
      "name"        -> micrositeName.value,
      "description" -> micrositeDescription.value,
      "version"     -> version.value,
      "org"         -> organizationName.value,
      "baseurl"     -> baseUrl,
      "docs"        -> true,
      "markdown"    -> "kramdown",
      "highlighter" -> "rouge",
      "collections" -> Map("tut" -> Map("output" -> true))
    )

    val userCustomVariables = micrositeConfigYaml.value
    val configWithAllCustomVariables = userCustomVariables.copy(
      yamlCustomProperties = defaultYamlCustomVariables ++ userCustomVariables.yamlCustomProperties)

    new MicrositeHelper(
      MicrositeSettings(
        identity = MicrositeIdentitySettings(
          name = micrositeName.value,
          description = micrositeDescription.value,
          author = micrositeAuthor.value,
          homepage = micrositeHomepage.value,
          organizationHomepage = micrositeOrganizationHomepage.value,
          twitter = micrositeTwitter.value,
          twitterCreator = micrositeTwitterCreator.value,
          analytics = micrositeAnalyticsToken.value
        ),
        visualSettings = MicrositeVisualSettings(
          highlightTheme = micrositeHighlightTheme.value,
          palette = micrositePalette.value,
          favicons = micrositeFavicons.value
        ),
        configYaml = configWithAllCustomVariables,
        fileLocations = MicrositeFileLocations(
          micrositeImgDirectory = micrositeImgDirectory.value,
          micrositeCssDirectory = micrositeCssDirectory.value,
          micrositeJsDirectory = micrositeJsDirectory.value,
          micrositeCDNDirectives = micrositeCDNDirectives.value,
          micrositeExternalLayoutsDirectory = micrositeExternalLayoutsDirectory.value,
          micrositeExternalIncludesDirectory = micrositeExternalIncludesDirectory.value,
          micrositeDataDirectory = micrositeDataDirectory.value,
          micrositeExtraMdFiles = micrositeExtraMdFiles.value,
          micrositeExtraMdFilesOutput = micrositeExtraMdFilesOutput.value
        ),
        urlSettings = MicrositeUrlSettings(
          micrositeBaseUrl = micrositeBaseUrl.value,
          micrositeDocumentationUrl = micrositeDocumentationUrl.value
        ),
        micrositeKazariSettings = KazariSettings(
          micrositeKazariEvaluatorUrl.value,
          micrositeKazariEvaluatorToken.value,
          micrositeKazariGithubToken.value,
          micrositeKazariCodeMirrorTheme.value,
          micrositeKazariDependencies.value,
          micrositeKazariResolvers.value
        ),
        gitSettings = MicrositeGitSettings(
          githubOwner = micrositeGitHostingService.value match {
            case GitHub => micrositeGithubOwner.value
            case _      => ""
          },
          githubRepo = micrositeGitHostingService.value match {
            case GitHub => micrositeGithubRepo.value
            case _      => ""
          },
          gitHostingService = micrositeGitHostingService.value.name,
          gitHostingUrl = micrositeGitHostingUrl.value,
          gitSidecarChat = micrositeGitterChannel.value,
          gitSidecarChatUrl = micrositeGitterChannelUrl.value
        )
      ))
  }

  lazy val micrositeTasksSettings = Seq(
    microsite := micrositeHelper.value.createResources(
      resourceManagedDir = (resourceManaged in Compile).value,
      tutSourceDirectory = (tutSourceDirectory in Compile).value),
    micrositeConfig := micrositeHelper.value
      .copyConfigurationFile((sourceDirectory in Jekyll).value, siteDirectory.value),
    micrositeMakeExtraMdFiles := micrositeHelper.value.buildAdditionalMd(),
    micrositeTutExtraMdFiles := {
      val r     = (runner in Tut).value
      val in    = micrositeMakeExtraMdFiles.value
      val out   = tutTargetDirectory.value
      val cp    = (fullClasspath in Tut).value
      val opts  = (scalacOptions in Tut).value
      val pOpts = tutPluginJars.value.map(f => "–Xplugin:" + f.getAbsolutePath)
      val re    = tutNameFilter.value.pattern.toString
      _root_.tut.TutPlugin.tutOne(streams.value, r, in, out, cp, opts, pOpts, re).map(_._1)
    },
    makeMicrosite := {
      Def
        .sequential(
          microsite,
          tut,
          micrositeTutExtraMdFiles,
          makeSite,
          micrositeConfig
        )
        .value
    },
    publishMicrosite := Def.task {
      Command.process(publishMicrositeCommandKey, state.value)
      (): Unit
    }.value
  )

  val publishMicrositeCommand: Command = Command(publishMicrositeCommandKey)(_ => OptNotSpace) {
    (st, _) =>
      val extracted = Project.extract(st)

      val siteDir: File                 = extracted.get(target in makeSite)
      val noJekyll: Boolean             = extracted.get(ghpagesNoJekyll)
      val branch: String                = extracted.get(ghpagesBranch)
      val pushSiteWith: PushWith        = extracted.get(micrositePushSiteWith)
      val gitHosting: GitHostingService = extracted.get(micrositeGitHostingService)
      val githubOwner: String           = extracted.get(micrositeGithubOwner)
      val githubRepo: String            = extracted.get(micrositeGithubRepo)
      val githubToken: Option[String]   = extracted.get(micrositeGithubToken)

      val cleanState: State = extracted.runTask(clean, st)._1
      val makeState: State  = extracted.runTask(makeMicrosite, cleanState)._1

      (pushSiteWith.name, gitHosting.name) match {
        case (GHPagesPlugin.name, _) =>
          val ref = extracted.get(thisProjectRef)
          extracted.runAggregated[Unit](ghpagesPushSite in Global in ref, makeState)
        case (GitHub4s.name, GitHub.name) if githubToken.nonEmpty =>
          val commitMessage = sys.env.getOrElse("SBT_GHPAGES_COMMIT_MESSAGE", "updated site")
          makeState.log.info(
            s"""Committing files from ${siteDir.getAbsolutePath} into branch '$branch'
               | * repo: $githubOwner/$githubRepo
               | * commitMessage: $commitMessage""".stripMargin)
          val ghOps: GitHubOps = new GitHubOps(githubOwner, githubRepo, githubToken)
          if (noJekyll) IO.touch(siteDir / ".nojekyll")
          ghOps.commitDir(branch, commitMessage, siteDir) match {
            case Right(_) => makeState.log.info("Success committing files")
            case Left(e) =>
              makeState.log.error(s"Error committing files")
              e.printStackTrace()
          }
          makeState
        case (GitHub4s.name, GitHub.name) =>
          makeState.log.error(
            s"You must provide a GitHub token through the `micrositeGithubToken` setting for pushing with github4s")
          makeState
        case (GitHub4s.name, hosting) =>
          makeState.log.warn(s"github4s doens't have support for $hosting")
          makeState
        case _ =>
          makeState.log.error(
            s"""Unexpected match case (pushSiteWith, gitHosting) = ("${pushSiteWith.name}", "${gitHosting.name}")""")
          makeState
      }
  }
}
