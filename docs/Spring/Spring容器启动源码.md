# Spring容器启动源码

首先声明：看本篇文章之前需要先看[这篇文章]([study-notes/Sping核心组件介绍.md at master · ZhaoCharles/study-notes (github.com)](https://github.com/ZhaoCharles/study-notes/blob/master/docs/Spring/Sping核心组件介绍.md))，以防止由于对一些组件不熟悉导致的阅读困难。

Spring中包含多个容器的实现，本篇我们以Spring boot使用的AnnotationConfigApplicationContext容器为例来介绍容器的启动到底做了些什么。需要提前说明的是，由于容器启动时的有些操作较复杂，因此将这些内容放在后面的文章中单独介绍。另外，本文中粘贴的Spring源码删除了一些不重要的部分，因此与源码会有些差异。

在介绍容器启动之前我们先来了解下AnnotationConfigApplicationContext类的类继承结构：

![AnnotationConfigApplicationContext类的类继承结构](D:\plan\AnnotationConfigApplicationContext.png)

### Spring容器的使用

让我们再来回忆一下Spring的容器是怎么使用的，首先我们提供一个给Spring容器管理的类，如下：

```java
@Component
public class StudentService {

   public void introduce() {
      System.out.println("My name is Janny!");
   }
}
```

还需要一个配置类来告诉Spring容器需要扫描的包路径。

```java
@ComponentScan("com.charles")
public class Config {
}
```

所谓的容器的启动其实就是创建一个容器对象，容器的创建和使用如下：

```java
public class SpringTest {

   public static void main(String[] args) {
      AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(Config.class);
      StudentService studentService = (StudentService) context.getBean("studentService");
      studentService.introduce();
   }
}
```

我们今天就从new AnnotationConfigApplication(Config.class)开始。

### AnnotationConfigApplicationContext的创建

首先我们进入到AnnotationConfigApplicationContext的构造函数①：

```java
public AnnotationConfigApplicationContext(Class<?>... componentClasses) {
   this();
   register(componentClasses);
   refresh();
}
```

该构造函数调用了另一个无参构造函数②：

```java
public AnnotationConfigApplicationContext() {
   this.reader = new AnnotatedBeanDefinitionReader(this);
   createAnnotatedBeanDefReader.end();
   this.scanner = new ClassPathBeanDefinitionScanner(this);
}
```

然而以上却不是容器创建第一步要做的事，因为子类在创建时要先调用父类的构造方法，因此我们需要先看其父类GenericApplicationContext的无参构造函数：

```java
public GenericApplicationContext() {
   this.beanFactory = new DefaultListableBeanFactory();
}
```

可以看到容器启动的第一个操作是创建了一个DefaultListableBeanFactory对象，毕竟bean的管理功能是依靠BeanFactory实现的，因此可以理解。

现在我们回到②，在AnnotationConfigApplicationContext构造函数中还创建了BeanDefinition的两个解析构造器AnnotatedBeanDefinitionReader和ClassPathBeanDefinitionScanner，进入到AnnotatedBeanDefinitionReader的构造函数③：

```java
public AnnotatedBeanDefinitionReader(BeanDefinitionRegistry registry, Environment environment) {
   this.registry = registry;
   this.conditionEvaluator = new ConditionEvaluator(registry, environment, null);
   AnnotationConfigUtils.registerAnnotationConfigProcessors(this.registry);
}
```

此处的BeanDefinition管理器BeanDefinitionRegistry















































