# Spring容器启动源码

首先声明：看本篇文章之前需要先看[这篇文章](https://github.com/ZhaoCharles/study-notes/blob/master/docs/Spring/Sping核心组件介绍.md)，以防止由于对一些组件不熟悉导致的阅读困难，并且由于Spring源码较为复杂，本文使用类似①②③表示某个方法以使得方法之间的跳转更明晰。

Spring中包含多个容器的实现，本篇我们以Spring boot使用的AnnotationConfigApplicationContext容器为例来介绍容器的启动到底做了些什么。需要提前说明的是，由于容器启动时的有些操作较复杂，因此将这些内容放在后面的文章中单独介绍。另外，本文中粘贴的Spring源码删除了一些不重要的部分，因此与源码会有些差异。

在介绍容器启动之前我们先来了解下AnnotationConfigApplicationContext类的类继承结构：

![AnnotationConfigApplicationContext类的类继承结构](D:\plan\AnnotationConfigApplicationContext.png)

### 一、Spring容器的使用

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

### 二、AnnotationConfigApplicationContext容器的启动

首先进入到AnnotationConfigApplicationContext的构造函数①：

```java
public AnnotationConfigApplicationContext(Class<?>... componentClasses) {
   this();
   register(componentClasses);
   refresh();
}
```

该构造函数的核心是refersh()方法，我们先来看refresh()方法前做了哪些准备工作。

#### 1.refresh()前的准备工作

在构造函数①中首先调用了另一个无参构造函数②，在这个构造函数中分别初始化了AnnotatedBeanDefinitionReader（专门用于解析注解并生成BeanDefinition）和ClassPathBeanDefinitionScanner（主要用来扫描@ComponentScan注解指定的包路径并生成BeanDefinition）。

```java
public AnnotationConfigApplicationContext() {
   this.reader = new AnnotatedBeanDefinitionReader(this);
   createAnnotatedBeanDefReader.end();
   this.scanner = new ClassPathBeanDefinitionScanner(this);
}
```

然而以上却不是容器创建第一步要做的事，由于子类在创建时要先调用父类的构造方法，因此我们需要先看其父类GenericApplicationContext的无参构造函数：

```java
public GenericApplicationContext() {
   this.beanFactory = new DefaultListableBeanFactory();
}
```

可以看到容器启动的第一个操作是创建了一个DefaultListableBeanFactory对象，毕竟bean的管理功能是依靠BeanFactory实现的，因此可以理解。

##### AnnotatedBeanDefinitionReader初始化

现在我们回到构造函数②，进入到AnnotatedBeanDefinitionReader的构造函数③：

```java
public AnnotatedBeanDefinitionReader(BeanDefinitionRegistry registry, Environment environment) {
   this.registry = registry;
   this.conditionEvaluator = new ConditionEvaluator(registry, environment, null);
   AnnotationConfigUtils.registerAnnotationConfigProcessors(this.registry);
}
```

此处的BeanDefinition管理器BeanDefinitionRegistry指的就是AnnotationConfigApplicationContext（其父类GenericApplicationContext实现了BeanDefinitionRegistry接口），方法的第二行初始化了一个@Conditional注解的解析器。

ApplicationBeanDefinitionReader构造函数的核心在于第三行代码，进入第三行的方法，这个方法的重要源码如下：

```java
public static Set<BeanDefinitionHolder> registerAnnotationConfigProcessors(
      BeanDefinitionRegistry registry, @Nullable Object source) {

   DefaultListableBeanFactory beanFactory = unwrapDefaultListableBeanFactory(registry);
   if (beanFactory != null) {

      //设置DefaultListableBeanFactory的比较器
      if (!(beanFactory.getDependencyComparator() instanceof AnnotationAwareOrderComparator)) {
         beanFactory.setDependencyComparator(AnnotationAwareOrderComparator.INSTANCE);
      }
      //设置DefaultListableBeanFactory中判断某个Bean是否支持自动注入以及判断是否是懒注入的解析器
      if (!(beanFactory.getAutowireCandidateResolver() instanceof ContextAnnotationAutowireCandidateResolver)) {
         beanFactory.setAutowireCandidateResolver(new ContextAnnotationAutowireCandidateResolver());
      }
   }

   Set<BeanDefinitionHolder> beanDefs = new LinkedHashSet<>(8);

   // 向DefaultListableBeanFactory中注册ConfigurationClassPostProcessor的BeanDefinition
   if (!registry.containsBeanDefinition(CONFIGURATION_ANNOTATION_PROCESSOR_BEAN_NAME)) {
      RootBeanDefinition def = new RootBeanDefinition(ConfigurationClassPostProcessor.class);
      def.setSource(source);
      beanDefs.add(registerPostProcessor(registry, def, CONFIGURATION_ANNOTATION_PROCESSOR_BEAN_NAME));
   }

   // 向DefaultListableBeanFactory中注册AutowiredAnnotationBeanPostProcessor的BeanDefinition
   if (!registry.containsBeanDefinition(AUTOWIRED_ANNOTATION_PROCESSOR_BEAN_NAME)) {
      RootBeanDefinition def = new RootBeanDefinition(AutowiredAnnotationBeanPostProcessor.class);
      def.setSource(source);
      beanDefs.add(registerPostProcessor(registry, def, AUTOWIRED_ANNOTATION_PROCESSOR_BEAN_NAME));
   }

   // 向DefaultListableBeanFactory中注册CommonAnnotationBeanPostProcessor类型的BeanDefinition
   if (jsr250Present && !registry.containsBeanDefinition(COMMON_ANNOTATION_PROCESSOR_BEAN_NAME)) {
      RootBeanDefinition def = new RootBeanDefinition(CommonAnnotationBeanPostProcessor.class);
      def.setSource(source);
      beanDefs.add(registerPostProcessor(registry, def, COMMON_ANNOTATION_PROCESSOR_BEAN_NAME));
   }

   // 向DefaultListableBeanFactory中注册PersistenceAnnotationBeanPostProcessor类型的BeanDefinition
   if (jpaPresent && !registry.containsBeanDefinition(PERSISTENCE_ANNOTATION_PROCESSOR_BEAN_NAME)) {
      RootBeanDefinition def = new RootBeanDefinition();
      try {
         def.setBeanClass(ClassUtils.forName(PERSISTENCE_ANNOTATION_PROCESSOR_CLASS_NAME,
               AnnotationConfigUtils.class.getClassLoader()));
      }
      catch (ClassNotFoundException ex) {
         throw new IllegalStateException(
               "Cannot load optional framework class: " + PERSISTENCE_ANNOTATION_PROCESSOR_CLASS_NAME, ex);
      }
      def.setSource(source);
      beanDefs.add(registerPostProcessor(registry, def, PERSISTENCE_ANNOTATION_PROCESSOR_BEAN_NAME));
   }

   // 向DefaultListableBeanFactory中注册EventListenerMethodProcessor类型的BeanDefinition
   if (!registry.containsBeanDefinition(EVENT_LISTENER_PROCESSOR_BEAN_NAME)) {
      RootBeanDefinition def = new RootBeanDefinition(EventListenerMethodProcessor.class);
      def.setSource(source);
      beanDefs.add(registerPostProcessor(registry, def, EVENT_LISTENER_PROCESSOR_BEAN_NAME));
   }

   // 向DefaultListableBeanFactory中注册DefaultEventListenerFactory类型的BeanDefinition
   if (!registry.containsBeanDefinition(EVENT_LISTENER_FACTORY_BEAN_NAME)) {
      RootBeanDefinition def = new RootBeanDefinition(DefaultEventListenerFactory.class);
      def.setSource(source);
      beanDefs.add(registerPostProcessor(registry, def, EVENT_LISTENER_FACTORY_BEAN_NAME));
   }

   return beanDefs;
}
```

该方法主要做了以下操作：

1. 拿到AnnotationConfigApplicationContext在之前创建的DefaultListableBeanFactory对象；

2. 设置DefaultListableBeanFactory的比较器，以便于后面使用；

3. 设置DefaultListableBeanFactory中判断某个Bean是否支持自动注入以及判断是否是懒注入的解析器；

4. 向DefaultListableBeanFactory中注册ConfigurationClassPostProcessor的BeanDefinition，ConfigurationClassPostProcessor是一个BeanFactoryPostProcessor，主要负责解析配置类；

5. 向DefaultListableBeanFactory中注册AutowiredAnnotationBeanPostProcessor的BeanDefinition，AutowiredAnnotationBeanPostProcessor是一个InstantiationAwareBeanPostProcessor，主要负责实例化时推断使用哪个构造方法以及@AutoWired注解的依赖注入；

6. 向DefaultListableBeanFactory中注册CommonAnnotationBeanPostProcessor的BeanDefinition，CommonAnnotationBeanPostProcessor是一个InstantiationAwareBeanPostProcessor，主要负责@Resource、@PostConstruct和@PreDestroy注解的支持；

6. 向DefaultListableBeanFactory中注册PersistenceAnnotationBeanPostProcessor类型的BeanDefinition，PersistenceAnnotationBeanPostProcessor是一个InstantiationAwareBeanPostProcessor，主要是对@PersistenceUnit和@PersistenceContext注解的支持，这两个注解与JPA有关；

6. 向DefaultListableBeanFactory中注册EventListenerMethodProcessor类型的BeanDefinition，EventListenerMethodProcessor是一个BeanFactoryPostProcessor，同时也是一个SmartInitializingSingleton，主要负责@EventListener注解的处理，与事件监听机制有关；

9. 向DefaultListableBeanFactory中注册DefaultEventListenerFactory类型的BeanDefinition，主要也是支持@EventListener注解的，后续我们会详细介绍ApplicationContext的事件监听机制。


向DefaultListableBeanFactory注册BeanDefinition实际上就是在DefaultListableBeanFactory中一个保存BeanDefinition的Map中添加了一个BeanDefinition。registerPostProcessor()方法里面调用了DefaultListableBeanFactory的registerBeanDefinition()方法，具体实现如下：

   ```java
   public void registerBeanDefinition(String beanName, BeanDefinition beanDefinition)
         throws BeanDefinitionStoreException {
      BeanDefinition existingDefinition = this.beanDefinitionMap.get(beanName);
      if (existingDefinition != null) {
         // 默认是允许BeanDefinition覆盖的，关于BeanDefinition是否可覆盖不是重点，因此不纠结
         this.beanDefinitionMap.put(beanName, beanDefinition);
      }
   }
   ```

在容器启动的一开始就将以上几个BeanDefinition放入容器的BeanDefinition池，是由于在启动过程中就需要使用它们，此处暂且记下注册的这几个BeanDefinition，后面使用时再详细介绍。

##### ClassPathBeanDefinitionScanner初始化

ClassPathBeanDefinitionScanner主要负责扫描@ComponentScan注解执行的包路径下面的Bean并且生成BeanDefinition。其主要构造函数如下：

```java
public ClassPathBeanDefinitionScanner(BeanDefinitionRegistry registry, boolean useDefaultFilters,
      Environment environment, @Nullable ResourceLoader resourceLoader) {
   this.registry = registry;

   if (useDefaultFilters) {
      //设置扫描过滤器
      registerDefaultFilters();
   }
    //设置环境对象
   setEnvironment(environment);
    //设置ResourceLoader
   setResourceLoader(resourceLoader);
}
```

我们主要介绍一下扫描过滤器。我们都知道Spring在扫描的时候会将路径下带有@Component注解的类注册成Bean到容器中，事实上ClassPathBeanDefinitionScanner在扫描的时候并不是只扫描@Component注解标记的类，而是根据扫描过滤器的规则来生成BeanDefinition。

扫描过滤器共有两种，分别为includeFilters（包含过滤器）和excludeFilters（不包含过滤器），ClassPathBeanDefinitionScanner扫描时认为只要满足了includeFilters中的任意一个包含过滤器条件的类是Bean，相对应的，会忽略满足excludeFilters中的任意一个不包含过滤器的类。

```java
private final List<TypeFilter> includeFilters = new ArrayList<>();

private final List<TypeFilter> excludeFilters = new ArrayList<>();
```

现在回到ClassPathBeanDefinitionScanner的构造函数中的设置扫描过滤器方法：

```java
protected void registerDefaultFilters() {

   // 将@Component注解添加到包含过滤器中
   this.includeFilters.add(new AnnotationTypeFilter(Component.class));

   ClassLoader cl = ClassPathScanningCandidateComponentProvider.class.getClassLoader();

   try {
      this.includeFilters.add(new AnnotationTypeFilter(
            ((Class<? extends Annotation>) ClassUtils.forName("javax.annotation.ManagedBean", cl)), false));
      logger.trace("JSR-250 'javax.annotation.ManagedBean' found and supported for component scanning");
   }
   catch (ClassNotFoundException ex) {
      // JSR-250 1.1 API (as included in Java EE 6) not available - simply skip.
   }

   try {
      this.includeFilters.add(new AnnotationTypeFilter(
            ((Class<? extends Annotation>) ClassUtils.forName("javax.inject.Named", cl)), false));
      logger.trace("JSR-330 'javax.inject.Named' annotation found and supported for component scanning");
   }
   catch (ClassNotFoundException ex) {
      // JSR-330 API not available - simply skip.
   }
}
```

可以看到@Component注解在这时就已经被添加到了包含过滤器中，因此ClassPathBeanDefinitionScanner在扫描时才会将带有@Component注解的类注册为Bean。

除了在ClassPathBeanDefinitionScanner初始化时Spring容器自动添加@Component注解类型的包含过滤器，我们还可以在配置类上的@ComponentScan注解的属性includeFilters和excludeFilters。例如我们将StudentService类上的@Component注解去掉，再修改配置类上的@ComponentScan注解如下，Spring照样会将StundentService注册为Bean。

```java
@ComponentScan(value = "com.charles",includeFilters = {
      @ComponentScan.Filter(
            type = FilterType.ASSIGNABLE_TYPE,
            classes = StudentService.class
      )
})
public class Config {
}
```

过滤器的类型分为以下五种，每种过滤器都拥有自己的过滤器匹配逻辑，此处仅简单介绍过滤器种类和作用，不再详细看过滤器逻辑。

- ANNOTATION：判断是否包含某个注解，例如ClassPathBeanDefinitionScanner初始化时添加的@Component注解类型的包含过滤器就是属于这种类型；
- ASSIGNABLE_TYPE：判断是否是某个类，例如上面举例添加的StudentService过滤器就是这种类型；
- ASPECTJ：判断是否满足某个Aspectj表达式；
- REGEX：判断是否满足某个正则表达式；
- CUSTOM：判断是否满足我们自定义的TypeFilter。

##### AnnotatedBeanDefinitionReader解析注册BeanDefinition

以上我们只是介绍了AnnotationConfigApplicationContext的父类构造函数和构造函数①调用的另一个无参构造函数的操作，在调用refresh()方法前，构造函数①还借助刚刚在无参构造函数中初始化完成的AnnotatedBeanDefinitionReader解析传入的配置类并注册对应的BeanDefinition，借此我们来看看AnnotatedBeanDefinitionReader解析注册BeanDefinition的过程。

AnnotatedBeanDefinitionReader解析生成BeanDefinition最终调用的方法如下：

```java
private <T> void doRegisterBean(Class<T> beanClass, @Nullable String name,
      @Nullable Class<? extends Annotation>[] qualifiers, @Nullable Supplier<T> supplier,
      @Nullable BeanDefinitionCustomizer[] customizers) {
	//AnnotatedGenericBeanDefinition是BeanDefinition的一种
   AnnotatedGenericBeanDefinition abd = new AnnotatedGenericBeanDefinition(beanClass);
   if (this.conditionEvaluator.shouldSkip(abd.getMetadata())) {
      return;
   }

   abd.setInstanceSupplier(supplier);
   // 解析@Scope注解
   ScopeMetadata scopeMetadata = this.scopeMetadataResolver.resolveScopeMetadata(abd);
   abd.setScope(scopeMetadata.getScopeName());
    //此处传入的name=null，因此直接使用默认方法生成beanName，Config类的默认beanName=config
   String beanName = (name != null ? name : this.beanNameGenerator.generateBeanName(abd, this.registry));

   AnnotationConfigUtils.processCommonDefinitionAnnotations(abd);
   if (qualifiers != null) {
      for (Class<? extends Annotation> qualifier : qualifiers) {
          // 解析@Primary注解
         if (Primary.class == qualifier) {
            abd.setPrimary(true);
         }
          // 解析@Lazy注解
         else if (Lazy.class == qualifier) {
            abd.setLazyInit(true);
         }
         else {
            abd.addQualifier(new AutowireCandidateQualifier(qualifier));
         }
      }
   }
   if (customizers != null) {
      for (BeanDefinitionCustomizer customizer : customizers) {
         customizer.customize(abd);
      }
   }
	// 将BeanDefinition和beanName封装
   BeanDefinitionHolder definitionHolder = new BeanDefinitionHolder(abd, beanName);
   definitionHolder = AnnotationConfigUtils.applyScopedProxyMode(scopeMetadata, definitionHolder, this.registry);
    //调用上面介绍过的DefaultListableFactory的registerBeanDefinition()方法注册BeanDefinition
   BeanDefinitionReaderUtils.registerBeanDefinition(definitionHolder, this.registry);
}
```

#### 2.refresh()方法

前面只是对DefaultListableFactory做了些初始化的设置以及另外一些准备工作，refresh()方法才是容器启动的核心，包的扫描、生成非懒加载的单例Bean等都是该方法内完成的。refresh()的实现如下：

```java
public void refresh() throws BeansException, IllegalStateException {
   synchronized (this.startupShutdownMonitor) {
	  // 1.refresh前的准备工作
      prepareRefresh();

      // 2.判断是否可重复刷新
      ConfigurableListableBeanFactory beanFactory = obtainFreshBeanFactory();

      // 3.设置BeanFactory
      prepareBeanFactory(beanFactory);

      try {
         // 4.子类设置BeanFactory
         postProcessBeanFactory(beanFactory);

         // 5.执行BeanFactoryPostProcessor接口方法、解析配置类、扫描Bean并注册BeanDefinition
         invokeBeanFactoryPostProcessors(beanFactory);

         // 6.将扫描到的BeanPostProcessors实例化并排序，然后添加到BeanFactory的beanPostProcessors属性中去
         registerBeanPostProcessors(beanFactory);

         beanPostProcess.end();

         // 7.判断是否扫描到了beanName为"messageSource"的bean，设置容器的messageSource属性以支持国际化功能
         initMessageSource();

         // 8.判断是否扫描到了beanName为“applicationEventMulticaster”的bean，以支持事件发布功能
         initApplicationEventMulticaster();

         // 9.模板方法
         onRefresh();

         // 10.把定义的ApplicationListener的Bean对象，设置到ApplicationContext中去，并执行在此之前所发布的事件
         registerListeners();

         // 11.实例化非懒加载的Bean
         finishBeanFactoryInitialization(beanFactory);

         // 12.发布容器初始化完成的事件
         finishRefresh();
      }

      catch (BeansException ex) {
         if (logger.isWarnEnabled()) {
            logger.warn("Exception encountered during context initialization - " +
                  "cancelling refresh attempt: " + ex);
         }

         destroyBeans();

         cancelRefresh(ex);

         throw ex;
      }

      finally {
         resetCommonCaches();
         contextRefresh.end();
      }
   }
}
```

下面我们对refresh()方法中的子方法一一做介绍，需要说明的是，有些内容过于复杂的方法我们抽离出来后面单独介绍，此处仅介绍其作用。

##### prepareRefresh()

这个方法主要是做一些refresh()前的准备工作。

```java
protected void prepareRefresh() {
   // 模板模式方法，提供给子类实现设置需要的环境变量等，例如
   initPropertySources();

   // 判断环境变量的合法性
   getEnvironment().validateRequiredProperties();
}
```

##### obtainFreshBeanFactory()

使用Spring容器时我们是可以直接调用refresh()方法的，这个方法主要是判断是否可以调用refresh()方法刷新。此方法同样是模板方法，对于不同的子类的实现也不同，主要有两种实现：①容器AnnotationConfigApplicationContext的父类GenericApplicationContext判断如果当前容器重复刷新，则抛出异常；②容器AnnotationConfigWebApplicationContext的父类AbstractRefreshableApplicationContext则允许重复刷新，其源码如下：

```java
protected final void refreshBeanFactory() throws BeansException {
    //判断当前容器是否已初始化BeanFactory容器
   if (hasBeanFactory()) {
       //销毁已存在的容器保存的包括单例池在内的一些Map对象
      destroyBeans();
       //管理BeanFactory，即将当前容器的beanFactory置为null
      closeBeanFactory();
   }
   try {
       //创建新的BeanFactory容器
      DefaultListableBeanFactory beanFactory = createBeanFactory();
      beanFactory.setSerializationId(getId());
      customizeBeanFactory(beanFactory);
      loadBeanDefinitions(beanFactory);
      this.beanFactory = beanFactory;
   }
   catch (IOException ex) {
      throw new ApplicationContextException("I/O error parsing bean definition source for " + getDisplayName(), ex);
   }
}
```

##### prepareBeanFactory()

这个方法仍然是在设置BeanFactory容器，包括设置Spring EL表达式解析器、类型转化器等等。

```java
protected void prepareBeanFactory(ConfigurableListableBeanFactory beanFactory) {
   // 设置类加载器
   beanFactory.setBeanClassLoader(getClassLoader());

   // 设置Spring EL表达式解析器
   if (!shouldIgnoreSpel) {
      beanFactory.setBeanExpressionResolver(new StandardBeanExpressionResolver(beanFactory.getBeanClassLoader()));
   }

   // 设置类型转化器
   beanFactory.addPropertyEditorRegistrar(new ResourceEditorRegistrar(this, getEnvironment()));

   // 添加一个BeanPostProcessor，主要用来在Bean初始化前处理ApplicationContextAware等Aware接口回调
   beanFactory.addBeanPostProcessor(new ApplicationContextAwareProcessor(this));

   // 接口实现了存在ignoredDependencyInterfaces集合中的接口时，实现的set方法不会被Spring自动注入
   beanFactory.ignoreDependencyInterface(EnvironmentAware.class);
   beanFactory.ignoreDependencyInterface(EmbeddedValueResolverAware.class);
   beanFactory.ignoreDependencyInterface(ResourceLoaderAware.class);
   beanFactory.ignoreDependencyInterface(ApplicationEventPublisherAware.class);
   beanFactory.ignoreDependencyInterface(MessageSourceAware.class);
   beanFactory.ignoreDependencyInterface(ApplicationContextAware.class);
   beanFactory.ignoreDependencyInterface(ApplicationStartupAware.class);

   // 容器使用byType进行依赖注入时会首先从resolvableDependencies集合中查找Bean
   beanFactory.registerResolvableDependency(BeanFactory.class, beanFactory);
   beanFactory.registerResolvableDependency(ResourceLoader.class, this);
   beanFactory.registerResolvableDependency(ApplicationEventPublisher.class, this);
   beanFactory.registerResolvableDependency(ApplicationContext.class, this);

   // 添加一个BeanPostProcessor，主要用来注册ApplicantsListener类型的Bean
   beanFactory.addBeanPostProcessor(new ApplicationListenerDetector(this));

   // Aspectj本身是通过编译期进行代理的，在Spring中就跟LoadTimeWeaver有关
   if (!NativeDetector.inNativeImage() && beanFactory.containsBean(LOAD_TIME_WEAVER_BEAN_NAME)) {
      beanFactory.addBeanPostProcessor(new LoadTimeWeaverAwareProcessor(beanFactory));
      beanFactory.setTempClassLoader(new ContextTypeMatchClassLoader(beanFactory.getBeanClassLoader()));
   }

   // 添加一些环境变量对象到单例池中
   if (!beanFactory.containsLocalBean(ENVIRONMENT_BEAN_NAME)) {
      beanFactory.registerSingleton(ENVIRONMENT_BEAN_NAME, getEnvironment());
   }
   if (!beanFactory.containsLocalBean(SYSTEM_PROPERTIES_BEAN_NAME)) {
      beanFactory.registerSingleton(SYSTEM_PROPERTIES_BEAN_NAME, getEnvironment().getSystemProperties());
   }
   if (!beanFactory.containsLocalBean(SYSTEM_ENVIRONMENT_BEAN_NAME)) {
      beanFactory.registerSingleton(SYSTEM_ENVIRONMENT_BEAN_NAME, getEnvironment().getSystemEnvironment());
   }
   if (!beanFactory.containsLocalBean(APPLICATION_STARTUP_BEAN_NAME)) {
      beanFactory.registerSingleton(APPLICATION_STARTUP_BEAN_NAME, getApplicationStartup());
   }
}
```

##### invokeBeanFactoryPostProcessors()

此方法主要作用是执行BeanFactoryPostProcessor接口方法、解析配置类、扫描Bean并注册BeanDefinition，由于解析配置类和扫描Bean逻辑较复杂，因此此处仅介绍BeanFactoryPostProcessor接口方法。

在分析此方法前，我们需要BeanFactoryPostProcessor接口和BeanDefinitionRegistryPostProcessor接口，这两个接口都提供了对BeanFactory，其中BeanDefinitionRegistryPostProcessor继承了BeanFactoryPostProcessor接口。BeanFactoryPostProcessor接口的定义如下：

```java
public interface BeanFactoryPostProcessor {
   void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException;

}
```

BeanDefinitionRegistryPostProcessor接口的定义如下：

```java
public interface BeanDefinitionRegistryPostProcessor extends BeanFactoryPostProcessor {
   void postProcessBeanDefinitionRegistry(BeanDefinitionRegistry registry) throws BeansException;

}
```

从这两个接口的参数类型可以看出来，postProcessBeanFactory()方法不支持向BeanFactory中注册新的BeanDefinition，而postProcessBeanDefinitionRegistry()方法支持，这是这两个接口的差异之一。另一个差异是这两个方法的执行顺序，这就需要回到invokeBeanFactoryPostProcessors()方法了。

```java
public static void invokeBeanFactoryPostProcessors(
      ConfigurableListableBeanFactory beanFactory, List<BeanFactoryPostProcessor> beanFactoryPostProcessors) {
    
   Set<String> processedBeans = new HashSet<>();

   if (beanFactory instanceof BeanDefinitionRegistry) {
      BeanDefinitionRegistry registry = (BeanDefinitionRegistry) beanFactory;
      List<BeanFactoryPostProcessor> regularPostProcessors = new ArrayList<>();
      List<BeanDefinitionRegistryPostProcessor> registryProcessors = new ArrayList<>();

      // 此时beanFactoryPostProcessors一般是空的，除非我们在手动调用refresh()方法之前主动向容器中添加了BeanFactoryPostProcessor
      for (BeanFactoryPostProcessor postProcessor : beanFactoryPostProcessors) {
         if (postProcessor instanceof BeanDefinitionRegistryPostProcessor) {
            BeanDefinitionRegistryPostProcessor registryProcessor =
                  (BeanDefinitionRegistryPostProcessor) postProcessor;
             //1.首先执行我们手动添加的BeanDefinitionRegistryPostProcessor的postProcessBeanDefinitionRegistry()方法
            registryProcessor.postProcessBeanDefinitionRegistry(registry);
            registryProcessors.add(registryProcessor);
         }
         else {
            regularPostProcessors.add(postProcessor);
         }
      }

      List<BeanDefinitionRegistryPostProcessor> currentRegistryProcessors = new ArrayList<>();

       //获取扫描到实现了PriorityOrdered接口的BeanDefinitionRegistryPostProcessor类型的bean
      String[] postProcessorNames =
            beanFactory.getBeanNamesForType(BeanDefinitionRegistryPostProcessor.class, true, false);
      for (String ppName : postProcessorNames) {
         if (beanFactory.isTypeMatch(ppName, PriorityOrdered.class)) {
            currentRegistryProcessors.add(beanFactory.getBean(ppName, BeanDefinitionRegistryPostProcessor.class));
            processedBeans.add(ppName);
         }
      }
      // 升序排序
      sortPostProcessors(currentRegistryProcessors, beanFactory);
      registryProcessors.addAll(currentRegistryProcessors);
       // 2.执行扫描出来实现了PriorityOrdered接口的BeanDefinitionRegistryPostProcessor的postProcessBeanDefinitionRegistry()方法
      invokeBeanDefinitionRegistryPostProcessors(currentRegistryProcessors, registry, beanFactory.getApplicationStartup());
      currentRegistryProcessors.clear();

      // 获取扫描到实现了Ordered接口的BeanDefinitionRegistryPostProcessor类型的bean
      postProcessorNames = beanFactory.getBeanNamesForType(BeanDefinitionRegistryPostProcessor.class, true, false);
      for (String ppName : postProcessorNames) {
         // processedBeans表示该beanFactoryPostProcessor的postProcessBeanDefinitionRegistry()方法已经执行过了，不再重复执行
         if (!processedBeans.contains(ppName) && beanFactory.isTypeMatch(ppName, Ordered.class)) {
            currentRegistryProcessors.add(beanFactory.getBean(ppName, BeanDefinitionRegistryPostProcessor.class));
            processedBeans.add(ppName);
         }
      }
      sortPostProcessors(currentRegistryProcessors, beanFactory);
      registryProcessors.addAll(currentRegistryProcessors);
       // 3.执行扫描出来实现了Ordered接口的BeanDefinitionRegistryPostProcessor的postProcessBeanDefinitionRegistry()方法
      invokeBeanDefinitionRegistryPostProcessors(currentRegistryProcessors, registry, beanFactory.getApplicationStartup());
      currentRegistryProcessors.clear();

      boolean reiterate = true;
      while (reiterate) {
         reiterate = false;
         postProcessorNames = beanFactory.getBeanNamesForType(BeanDefinitionRegistryPostProcessor.class, true, false);
         for (String ppName : postProcessorNames) {
            if (!processedBeans.contains(ppName)) {
               currentRegistryProcessors.add(beanFactory.getBean(ppName, BeanDefinitionRegistryPostProcessor.class));
               processedBeans.add(ppName);
               reiterate = true;
            }
         }
         sortPostProcessors(currentRegistryProcessors, beanFactory);
         registryProcessors.addAll(currentRegistryProcessors);
         // 4.执行扫描出来没有实现PriorityOrdered接口和Ordered接口的BeanDefinitionRegistryPostProcessor的postProcessBeanDefinitionRegistry()方法
         invokeBeanDefinitionRegistryPostProcessors(currentRegistryProcessors, registry, beanFactory.getApplicationStartup());
         currentRegistryProcessors.clear();
      }

      // 5.执行BeanDefinitionRegistryPostProcessor的postProcessBeanFactory()方法
      invokeBeanFactoryPostProcessors(registryProcessors, beanFactory);

      // 6.执行手动添加的BeanFactoryPostProcessor的postProcessBeanFactory()方法
      invokeBeanFactoryPostProcessors(regularPostProcessors, beanFactory);
   }

   else {
      invokeBeanFactoryPostProcessors(beanFactoryPostProcessors, beanFactory);
   }

   String[] postProcessorNames =
         beanFactory.getBeanNamesForType(BeanFactoryPostProcessor.class, true, false);

   List<BeanFactoryPostProcessor> priorityOrderedPostProcessors = new ArrayList<>();
   List<String> orderedPostProcessorNames = new ArrayList<>();
   List<String> nonOrderedPostProcessorNames = new ArrayList<>();
   // 根据是否实现PriorityOrdered和Ordered分批
   for (String ppName : postProcessorNames) {
      if (processedBeans.contains(ppName)) {

      }
      else if (beanFactory.isTypeMatch(ppName, PriorityOrdered.class)) {
         priorityOrderedPostProcessors.add(beanFactory.getBean(ppName, BeanFactoryPostProcessor.class));
      }
      else if (beanFactory.isTypeMatch(ppName, Ordered.class)) {
         orderedPostProcessorNames.add(ppName);
      }
      else {
         nonOrderedPostProcessorNames.add(ppName);
      }
   }

	// 7.执行实现了PriorityOrdered接口的BeanFactoryPostProcessor的postProcessBeanFactory()方法
   sortPostProcessors(priorityOrderedPostProcessors, beanFactory);
   invokeBeanFactoryPostProcessors(priorityOrderedPostProcessors, beanFactory);

   // 8.执行实现了Ordered接口的BeanFactoryPostProcessor的postProcessBeanFactory()方法
   List<BeanFactoryPostProcessor> orderedPostProcessors = new ArrayList<>(orderedPostProcessorNames.size());
   for (String postProcessorName : orderedPostProcessorNames) {
      orderedPostProcessors.add(beanFactory.getBean(postProcessorName, BeanFactoryPostProcessor.class));
   }
   sortPostProcessors(orderedPostProcessors, beanFactory);
   invokeBeanFactoryPostProcessors(orderedPostProcessors, beanFactory);

   // 9. 执行没有实现Ordered和PriorityOrdered接口的BeanFactoryPostProcessor的postProcessBeanFactory()方法
   List<BeanFactoryPostProcessor> nonOrderedPostProcessors = new ArrayList<>(nonOrderedPostProcessorNames.size());
   for (String postProcessorName : nonOrderedPostProcessorNames) {
      nonOrderedPostProcessors.add(beanFactory.getBean(postProcessorName, BeanFactoryPostProcessor.class));
   }
   invokeBeanFactoryPostProcessors(nonOrderedPostProcessors, beanFactory);

   beanFactory.clearMetadataCache();
}
```

可以看到这个方法主要是按照特定的顺序执行BeanDefinitionRegistryPostProcessor接口和BeanFactoryPostProcessor接口的方法，执行方法的顺序总结如下：

1. 首先执行我们手动添加的BeanDefinitionRegistryPostProcessor的postProcessBeanDefinitionRegistry()方法；
2. 执行扫描出来的实现了PriorityOrdered接口的BeanDefinitionRegistryPostProcessor的postProcessBeanDefinitionRegistry()方法；
3. 执行扫描出来的实现了Ordered接口的BeanDefinitionRegistryPostProcessor的postProcessBeanDefinitionRegistry()方法；
4. 执行扫描出来的没有实现PriorityOrdered接口和Ordered接口的BeanDefinitionRegistryPostProcessor的postProcessBeanDefinitionRegistry()方法；
5. 执行全部BeanDefinitionRegistryPostProcessor类型的postProcessBeanFactory()方法；
6. 执行手动添加的BeanFactoryPostProcessor的postProcessBeanFactory()方法；
7. 执行实现了PriorityOrdered接口的BeanFactoryPostProcessor的postProcessBeanFactory()方法；
8. 执行实现了Ordered接口的BeanFactoryPostProcessor的postProcessBeanFactory()方法；
9. 执行没有实现Ordered和PriorityOrdered接口的BeanFactoryPostProcessor的postProcessBeanFactory()方法。

##### registerBeanPostProcessors()

该方法的作用是将扫描到的BeanPostProcessor实例化并存储到BeanFactory容器的beanPostProcessors集合中以便后面调用。

```java
public static void registerBeanPostProcessors(
      ConfigurableListableBeanFactory beanFactory, AbstractApplicationContext applicationContext) {
	
    //获取扫描到的BeanPostProcessor类型的全部BeanName
   String[] postProcessorNames = beanFactory.getBeanNamesForType(BeanPostProcessor.class, true, false);
   // beanProcessorTargetCount表示BeanFactory中所有的BeanPostProcessor数量，+1表示BeanPostProcessorChecker
   int beanProcessorTargetCount = beanFactory.getBeanPostProcessorCount() + 1 + postProcessorNames.length;
   beanFactory.addBeanPostProcessor(new BeanPostProcessorChecker(beanFactory, beanProcessorTargetCount));


   List<BeanPostProcessor> priorityOrderedPostProcessors = new ArrayList<>();
   List<BeanPostProcessor> internalPostProcessors = new ArrayList<>();
   List<String> orderedPostProcessorNames = new ArrayList<>();
   List<String> nonOrderedPostProcessorNames = new ArrayList<>();
    // 首先根据BeanPostProcessor是否实现PriorityOrdered接口和Ordered接口分类，并实例化实现了PriorityOrdered接口的BeanPostProcessor
   for (String ppName : postProcessorNames) {
      if (beanFactory.isTypeMatch(ppName, PriorityOrdered.class)) {
         BeanPostProcessor pp = beanFactory.getBean(ppName, BeanPostProcessor.class);
         priorityOrderedPostProcessors.add(pp);
         if (pp instanceof MergedBeanDefinitionPostProcessor) {
            internalPostProcessors.add(pp);
         }
      }
      else if (beanFactory.isTypeMatch(ppName, Ordered.class)) {
         orderedPostProcessorNames.add(ppName);
      }
      else {
         nonOrderedPostProcessorNames.add(ppName);
      }
   }

   // 升序排序
   sortPostProcessors(priorityOrderedPostProcessors, beanFactory);
   registerBeanPostProcessors(beanFactory, priorityOrderedPostProcessors);

   // 然后实例化实现了Ordered接口的BeanPostProcessor
   List<BeanPostProcessor> orderedPostProcessors = new ArrayList<>(orderedPostProcessorNames.size());
   for (String ppName : orderedPostProcessorNames) {
      BeanPostProcessor pp = beanFactory.getBean(ppName, BeanPostProcessor.class);
      orderedPostProcessors.add(pp);
      if (pp instanceof MergedBeanDefinitionPostProcessor) {
         internalPostProcessors.add(pp);
      }
   }
   sortPostProcessors(orderedPostProcessors, beanFactory);
   registerBeanPostProcessors(beanFactory, orderedPostProcessors);

   // 最后实例化没有实现PriorityOrdered接口和Ordered接口的BeanPostProcessor
   List<BeanPostProcessor> nonOrderedPostProcessors = new ArrayList<>(nonOrderedPostProcessorNames.size());
   for (String ppName : nonOrderedPostProcessorNames) {
      BeanPostProcessor pp = beanFactory.getBean(ppName, BeanPostProcessor.class);
      nonOrderedPostProcessors.add(pp);
      if (pp instanceof MergedBeanDefinitionPostProcessor) {
         internalPostProcessors.add(pp);
      }
   }
   registerBeanPostProcessors(beanFactory, nonOrderedPostProcessors);

   // MergedBeanDefinitionPostProcessor排在最后
   sortPostProcessors(internalPostProcessors, beanFactory);
   registerBeanPostProcessors(beanFactory, internalPostProcessors);

   beanFactory.addBeanPostProcessor(new ApplicationListenerDetector(applicationContext));
}
```

### 三、AnnotationConfigApplicationContext启动流程图

![AnnotationConfigApplicationContext启动流程图](D:\JAVA\设计模式\Spring容器启动过程.png)


