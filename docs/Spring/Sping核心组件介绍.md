# Sping核心组件介绍

在学习Spring源码之前，我们首先来简单介绍Spring框架中较重要的一些概念和组件，了解了这些更有利于我们源码的学习。这些内容做到大概了解即可，后面介绍源码的时候它们的作用和形象就会慢慢立体起来，再回来看本篇文章就都明白了。

## Spring容器

Spring容器主要可以分为两种类型：基础类型的BeanFactory以及提供了一些高级功能的ApplicationContext容器。这两种容器的介绍和区别如下：

* BeanFactory：基础类型的IoC容器，提供完整的IoC服务支持。默认采用延迟初始化策略，即只有当客户端对象需要访问容器中的某个对象时才对该对象进行初始化和依赖注入动作。因此，容器启动速度较快，所需的资源也有限。
* ApplicationContext：ApplicationContext构建在BeanFactory的基础上，是比较高级的容器实现。除了拥有BeanFactory的所有支持外，ApplicationContext还提供了包括事件发布、国际化信息支持等高级功能。ApplicationContext管理的对象容器启动时就完成全部初始化，因此需要更多的系统资源，启动时间更长。


虽然现在最常用的是高级容器ApplicationContext，但在ApplicationContext作为一个IOC容器最关键的对象构建和管理的功能还是依赖BeanFactory容器实现的，可以把ApplicationContext看作是封装了BeanFactory容器并且提供了更多扩展和功能的容器。

BeanFactory和ApplicatinContext都是接口，它们都有很多实现类来应对不同的场景或者提供额外的不同的功能。BeanFactory的主要实现类是DefaultListableBeanFactory类，这个类是非常强大的，众多ApplicationContext容器的实现依赖的都是这个类。这一点在后面介绍源码时会有所体现。

DefaultListableFactory的类继承结构如下：

![DeafultListableFactory类图](D:\plan\DefaultListableBeanFactory.png "DeafultListableFactory类图")

可以看到DefaultListableFactory直接或间接地实现了很多接口，这些接口都有它们各自的功能，因此DefaultListableFactory类拥有很多功能。例如AliasRegistry接口提供了别名功能，FactoryBeanRegistrySupport提供了FactoryBean的功能，BeanDefinitionRegistry接口的注册BeanDefinition等操作等等一系列功能。关于DefaultListableFactory的功能后续介绍源码时再详细介绍。

ApplicationContext接口的实现类分别适用于不同的场景，其中较常用的两个实现类是ClassPathXmlApplicationContext和AnnotationConfigApplicationContext，它们都实现了AbstractApplicationContext类，最主要的区别是前者主要通过解析XML配置文件生成bean，后者主要通过解析注解修饰的配置类生成bean。Spring boot主要使用的是AnnotationConfigApplicationContext容器，因此我们以后主要介绍该实现类。

AnnotationConfigApplicationContext的类继承结构如下：

![AnnotationConfigApplicationContext类继承结构](D:\plan\FunctionalInterface.png "AnnotationConfigApplicationContext类继承结构")

可以看到AnnotationConfigApplicationContext类实现了BeanFactory之外还实现了其他的几个接口，其中MessageSource接口提供了国际化功能，ApplicationEventPublisher接口提供了广播事件的功能，ResourcePatternResolver接口提供了加载资源的功能等等。关于这些功能同样在后面会详细介绍，此处不再赘述。

## BeanDefinition

BeanDefinition表示Bean定义，该对象中几乎所有的属性都是用来描述Bean的。在Spring中，解析扫描到了我们定义的Bean之后并不是直接生成Bean，而是先构造BeanDefinition来描述Bean，在需要时便可以随时获取Bean的相关信息或者生成Bean。

BeanDefinition主要包含以下属性：

| 属性名            | 属性类型 | 说明                                                         |
| ----------------- | -------- | ------------------------------------------------------------ |
| beanClass         | Object   | 保存bean的Class对象，有时也保存类的全限定路径                |
| scope             | String   | bean的作用域，主要有单例Singleton和原型Prototype             |
| lazyInit          | Boolean  | 是否懒加载，非懒加载的单例bean在容器启动时就会创建，懒加载的单例bean在使用或依赖于该bean的bean加载时才会创建 |
| dependsOn         | String[] | 该bean依赖的bean的名称，在该bean实例化前需要将其依赖的bean先实例化 |
| primary           | boolean  | 当该类型的bean在容器中存在多个时，该bean是否是primary的      |
| initMethosName    | String   | bean初始化时执行的方法                                       |
| destroyMethodName | String   | bean销毁时执行的方法                                         |

## BeanDefinitionRegistry

BeanDefinitionRegistry主要负责BeanDefinition的管理，包括注册、删除、获取、判断是否已存在等功能。上面介绍过的DefaultListableBeanFactory类就实现了BeanDefinitionRegistry接口，在该类中使用一个Map来存储BeanDefinition，并通过实现BeanDefinitionRegistry接口的方法来操作这个Map。

## BeanDefinition解析构造器

BeanDefinition解析构造器的作用主要是通过解析注解或者XML或者以其他方式得到BeanDefinition，然后通过BeanDefinitionRegistry注册到容器中。在后续的源码中我们主要会接触到两种BeanDefinition解析构造器的实现：AnnotationBeanDefinitionReader和ClassPathBeanDefinitionScanner。

需要注意的是，Spring中大多数的功能（例如BeanDefinition注册）都是通过接口表达的，也就是某个接口拥有某些功能，其实现类也就拥有了这些功能。但此处我们介绍的BeanDefinition解析器不是这样的，AnnotationBeanDefinitionReader和ClassPathBeanDefinitionScanner并没有实现某个相同的接口，笔者觉得这里没有定义接口的原因是不同的解析构造器虽然最终实现的功能一致，但解析过程却相差甚远，无法做到统一，这一点在要介绍的这两个类就可以看出来。

#### AnnotationBeanDefinitionReader类

AnnotationBeanDefinitionReader类可以通过传入的Class对象直接解析该类得到对应的BeanDefinition对象，并且会解析该类上的@Scope、@Lazy、@Primary等注解，将这些注解的结果放到BeanDefinition对应的属性中，最后使用BeandefinitionRegistry注册到容器中。

#### ClassPathBeanDefinitionScanner类

ClassPathBeanDefinitionScanner可以扫描@ComponentScan注解指定的路径，得到所有我们定义的包含有比如@Component注解的类，最后将它们解析生成BeanDefinition，这些都是在容器启动时完成的。

## BeanPostProcessor

BeanPostProcessor是Bean的后置处理器，我们可以通过自定义一个实现Spring提供的具有不同扩展点的BeanPostProcessor中的某个接口的类来干涉Bean的创建过程。Spring提供的很多功能都是通过BeanPostProcessor实现的，比如我们熟知的依赖注入、AOP等功能。

## BeanFactoryPostProcessor

BeanFactoryPostProcessor与BeanPostProcessor名称相似，BeanPostProcessor是Bean的后置处理器，BeanFactoryPostProcessor当然就是BeanFactory的后置处理器了。

## FactoryBean

FactoryBean与BeanFactory名称相似，但其功能却是天差地别。BeanFactory是Spring容器，而FactoryBean只是一个特殊的Bean。它特殊就特殊在我们定义了一个FactoryBean，在容器中却会生成两个Bean。乍一看好像没啥用处，事实上Spring整合Mybatis框架时就使用到了FactoryBean。此处有个印象即可，我们不过多介绍，后面会详细介绍FactoryBean。

## **Metadata

Spring中还有一些以Metadata结尾的类或者接口，这些类或接口表示的是元数据。例如ClassMetadata表示的是类的元数据，AnnotationMetadata表示的是注解元数据。Spring在启动时并不会一股脑儿直接将扫描到的全部类都加载到JVM中，但有时候又需要类的某些信息，这时候就可以使用ASM技术（java字节码操作和分析框架）不需要加载Class文件的将需要的类的信息读取到元数据中，CGLIB动态代理使用的也是ASM技术。

## **Aware

以Aware接口的一些接口提供了一些回调函数，例如BeanNameAware接口提供了获取当前类的beanName的回调函数、ApplicationContextAware接口提供了获取当前容器对象的接口等等，这些回调函数在Bean初始化前被调用。
