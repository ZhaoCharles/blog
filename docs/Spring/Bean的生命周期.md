# Bean的生命周期

所谓Bean的生命周期，就是创建Bean到销毁Bean的过程。在这个过程中，Spring提供了很多扩展点，这些扩展点具象化的表示就是BeanPostProcessor（Bean的后置处理器）。本篇主要的内容就是介绍扫描Bean、创建Bean、Bean的后置处理以及销毁Bean的过程。

### Bean的生命周期流程图

直接上源码来学习Bean的生命周期不太容易理解，搭配下面的流程图可以使我们更好地理解Bean的生命周期。
