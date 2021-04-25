## *IntelliJ* Setup

Setting up *IntelliJ* is pretty simple.  There are a few plugins to install with the associated settings, and some settings to download and import.

### Plugins:
  1. *Google Java Format*
        1. *settings > Other settings > google-java-format*
        1.  Check 'enable'
      ![Screenshot from 2019-08-08 17-35-52
      ](https://user-images.githubusercontent.com/12397753/62746114-07cc2b80-ba03-11e9-9ac0-0b1e6e1e8788.png)
  1. *checkstyle-IDEA* [plugin](https://github.com/jshiell/checkstyle-idea)
        1. after install finish configuration in: **Other Settings > Checkstyle**
            1. load checkstyle file by clicking on the "plus" and navigating to the file .\IdeaProjects\triplea\config\checkstyle (If you can't find it, you can download it from [the repository](https://github.com/triplea-game/triplea/blob/master/config/checkstyle/checkstyle.xml))
            1. set checkstyle version
            1. set to scan all sources
      ![Screenshot from 2020-10-18 19-18-46
      ](https://user-images.githubusercontent.com/12397753/96394543-271e2700-1177-11eb-9460-24e2e235d60d.png)
  1. *Save Actions*
        1. **Settings > Other settings > Save Actions**
        1. Select 'Activate save actions on save'
        1. configure in settings to add 'final' to local variables and class variables.
  1. *Lombok*
        1. **settings > annotation processing**
        1. Turn on annotation processing.
        ![annotationprocessing2](https://user-images.githubusercontent.com/54828470/95939758-6da00a00-0da2-11eb-9c7a-823040578c4e.png)

### Settings
  1. *File > Import Settings*
  1. Select file: [.ide-intellij/intellij-settings.zip
   ](https://github.com/triplea-game/triplea/blob/master/.ide-intellij/intellij-settings.zip)
