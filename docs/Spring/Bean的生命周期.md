# Bean的生命周期

所谓Bean的生命周期，就是创建Bean到销毁Bean的过程。在这个过程中，Spring提供了很多扩展点，这些扩展点具象化的表示就是BeanPostProcessor（Bean的后置处理器）。本篇主要的内容就是介绍扫描Bean、创建Bean、执行Bean的后置处理器以及销毁Bean的过程。

### Bean的生命周期流程图

下面的流程图展示了Bean的整个生命周期，后面介绍的内容也是按照该流程图的顺序来介绍的。

![Bean的生命周期](https://github.com/ZhaoCharles/study-notes/blob/master/images/Bean的生命周期.png)

