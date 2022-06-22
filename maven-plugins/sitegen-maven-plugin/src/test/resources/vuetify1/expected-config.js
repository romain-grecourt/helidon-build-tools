function createConfig() {
    return {
        home: "home",
        release: "1.0",
        releases: [
            "1.0"
        ],
        pathColors: {
            "*": "blue-grey"
        },
        theme: {
            primary: '#1976D2',
            secondary: '#424242',
            accent: '#82B1FF',
            error: '#FF5252',
            info: '#2196F3',
            success: '#4CAF50',
            warning: '#FFC107'
        },
        navTitle: 'Pet Project doc',
        navIcon: 'import_contacts',
        navLogo: null
    };
}

function createRoutes(){
    return [
        {
            path: '/home',
            meta: {
                h1: 'Pet project',
                title: 'A pet project',
                h1Prefix: null,
                description: 'A wonderful project about nothing at all',
                keywords: 'keyword1, keyword2, keyword3',
                customLayout: null,
                hasNav: false
            },
            component: loadPage('home', 'home', {})
        },
        {
            path: '/about/01_intro',
            meta: {
                h1: 'Intro',
                title: 'Introduction',
                h1Prefix: null,
                description: 'introduction of pet project',
                keywords: 'keyword1, keyword2, keyword3',
                customLayout: null,
                hasNav: true
            },
            component: loadPage('about-01_intro', 'about/01_intro', {})
        },
        {
            path: '/about/02_contributing',
            meta: {
                h1: 'Contributing',
                title: 'Contributing',
                h1Prefix: null,
                description: 'Contributing to Pet project',
                keywords: 'keyword1, keyword2, keyword3',
                customLayout: null,
                hasNav: true
            },
            component: loadPage('about-02_contributing', 'about/02_contributing', {})
        },
        {
            path: '/about/03_admonitions',
            meta: {
                h1: 'Admonitions',
                title: 'Admonitions',
                h1Prefix: null,
                description: 'demonstrating admonitions',
                keywords: 'keyword1, keyword2, keyword3',
                customLayout: null,
                hasNav: true
            },
            component: loadPage('about-03_admonitions', 'about/03_admonitions', {})
        },
        {
            path: '/about/04_icons',
            meta: {
                h1: 'Icons',
                title: 'Icons',
                h1Prefix: null,
                description: 'demonstrating icons',
                keywords: 'keyword1, keyword2, keyword3',
                customLayout: null,
                hasNav: true
            },
            component: loadPage('about-04_icons', 'about/04_icons', {})
        },
        {
            path: '/about/05_example',
            meta: {
                h1: 'Examples',
                title: 'Examples',
                h1Prefix: null,
                description: 'demonstrating examples',
                keywords: 'keyword1, keyword2, keyword3',
                customLayout: null,
                hasNav: true
            },
            component: loadPage('about-05_example', 'about/05_example', {})
        },
        {
            path: '/about/06_tables',
            meta: {
                h1: 'Tables',
                title: 'Tables',
                h1Prefix: null,
                description: 'demonstrating tables',
                keywords: 'keyword1, keyword2, keyword3',
                customLayout: null,
                hasNav: true
            },
            component: loadPage('about-06_tables', 'about/06_tables', {})
        },
        {
            path: '/getting-started/01_prerequisites',
            meta: {
                h1: 'Pre-requisites',
                title: 'The pre-requisites',
                h1Prefix: null,
                description: 'The required pre-requisites',
                keywords: 'keyword1, keyword2, keyword3',
                customLayout: null,
                hasNav: true
            },
            component: loadPage('getting-started-01_prerequisites', 'getting-started/01_prerequisites', {})
        },
        {
            path: '/getting-started/02_development_settings',
            meta: {
                h1: 'Development settings',
                title: 'Development settings',
                h1Prefix: null,
                description: 'The settings for a development environment',
                keywords: 'keyword1, keyword2, keyword3',
                customLayout: null,
                hasNav: true
            },
            component: loadPage('getting-started-02_development_settings', 'getting-started/02_development_settings', {})
        },
        {
            path: '/lets-code/01_javascript',
            meta: {
                h1: 'Javascript',
                title: 'Let\'s code some Javascript',
                h1Prefix: null,
                description: 'This page shows some Javascript snippets',
                keywords: 'keyword1, keyword2, keyword3',
                customLayout: null,
                hasNav: true
            },
            component: loadPage('lets-code-01_javascript', 'lets-code/01_javascript', {})
        },
        {
            path: '/lets-code/02_java',
            meta: {
                h1: 'Java',
                title: 'Let\'s code some Java',
                h1Prefix: null,
                description: 'This page shows some Java snippets',
                keywords: 'keyword1, keyword2, keyword3',
                customLayout: null,
                hasNav: true
            },
            component: loadPage('lets-code-02_java', 'lets-code/02_java', {})
        },
        {
            path: '/lets-code/03_bash',
            meta: {
                h1: 'Bash',
                title: 'Let\'s code some Bash',
                h1Prefix: null,
                description: 'This page shows some bash snippets',
                keywords: 'keyword1, keyword2, keyword3',
                customLayout: null,
                hasNav: true
            },
            component: loadPage('lets-code-03_bash', 'lets-code/03_bash', {})
        },
        {
            path: '/lets-code/04_xml',
            meta: {
                h1: 'XML',
                title: 'Let\'s code some XML',
                h1Prefix: null,
                description: 'This page shows some XML snippets',
                keywords: 'keyword1, keyword2, keyword3',
                customLayout: null,
                hasNav: true
            },
            component: loadPage('lets-code-04_xml', 'lets-code/04_xml', {})
        },
        {
            path: '/playtime',
            meta: {
                h1: 'Play Time',
                title: 'Let\'s play',
                h1Prefix: null,
                description: 'This page shows a few games',
                keywords: 'keyword1, keyword2, keyword3',
                customLayout: null,
                hasNav: true
            },
            component: loadPage('playtime', 'playtime', {})
        },
        {
            path: '/', redirect: 'home'
        },
        {
            path: '*', redirect: '/'
        }
    ];
}
function createNav(){
    return [
        {
            title: null,
            pathprefix: null,
            depth: 4,
            items: [
                {
                    title: 'Cool Stuff',
                    pathprefix: null,
                    depth: 4,
                    items: [
                        {
                            title: 'What is it about?',
                            pathprefix: '/about',
                            depth: 4,
                            items: [
                                {
                                    title: 'Introduction',
                                    to: '/about/01_intro',
                                    action: null
                                },
                                {
                                    title: 'Contributing',
                                    to: '/about/02_contributing',
                                    action: null
                                },
                                {
                                    title: 'Admonitions',
                                    to: '/about/03_admonitions',
                                    action: null
                                },
                                {
                                    title: 'Icons',
                                    to: '/about/04_icons',
                                    action: null
                                },
                                {
                                    title: 'Examples',
                                    to: '/about/05_example',
                                    action: null
                                },
                                {
                                    title: 'Tables',
                                    to: '/about/06_tables',
                                    action: null
                                }
                            ],
                            action: 'weekend'
                        },
                        {
                            title: 'Getting started',
                            pathprefix: '/getting-started',
                            depth: 4,
                            items: [
                                {
                                    title: 'The pre-requisites',
                                    to: '/getting-started/01_prerequisites',
                                    action: null
                                },
                                {
                                    title: 'Development settings',
                                    to: '/getting-started/02_development_settings',
                                    action: null
                                }
                            ],
                            action: 'play_circle_outline'
                        }
                    ],
                    action: null
                },
                {
                    title: 'Boring Stuff',
                    pathprefix: null,
                    depth: 4,
                    items: [
                        {
                            title: 'Let\'s code!',
                            pathprefix: '/lets-code',
                            depth: 4,
                            items: [
                                {
                                    title: 'Let\'s code some Javascript',
                                    to: '/lets-code/01_javascript',
                                    action: null
                                },
                                {
                                    title: 'Let\'s code some Java',
                                    to: '/lets-code/02_java',
                                    action: null
                                },
                                {
                                    title: 'Let\'s code some Bash',
                                    to: '/lets-code/03_bash',
                                    action: null
                                },
                                {
                                    title: 'Let\'s code some XML',
                                    to: '/lets-code/04_xml',
                                    action: null
                                }
                            ],
                            action: 'code'
                        },
                        {
                            title: 'Play time!',
                            to: '/playtime',
                            action: 'home'
                        }
                    ],
                    action: null
                }
            ],
            action: null
        },
        {
            header: 'Additional Resources'
        },
        {
            title: 'Javadocs',
            href: 'https://docs.oracle.com/javase/8/docs/api/',
            target: '_blank',
            action: 'info'
        }
    ];
}
