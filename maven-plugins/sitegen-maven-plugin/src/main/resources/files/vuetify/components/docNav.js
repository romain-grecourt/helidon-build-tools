/*
 * Copyright (c) 2018, 2022 Oracle and/or its affiliates.
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

/* global Vue, navItems, config */

window.allComponents['docNav'] = {
    init: function () {
        // create a script element
        const scriptElt = document.createElement("script");
        scriptElt.id = "doc-nav";
        scriptElt.type = "text/x-template";
        scriptElt.text = `
            <v-navigation-drawer v-model="isActive" fixed dark app class="docNav">
                <v-toolbar flat dark class="transparent">
                    <v-list class="pa-0 vuetify">
                        <v-list-tile tag="div">
                            <v-list-tile-avatar>
                                <router-link to="/">
                                    <div v-if="navLogo" class="navLogo">
                                        <img :src="navLogo">
                                    </div>
                                    <v-icon v-else-if="navIcon">{{ navIcon }}</v-icon>
                                </router-link>
                            </v-list-tile-avatar>
                            <v-list-tile-content>
                                <v-list-tile-title>{{ navTitle }}</v-list-tile-title>
                                <v-list-tile-sub-title>
                                <v-menu v-if="releases.length > 1">
                                    <span flat slot="activator">
                                    Version: {{ release === releases[0] ? \`(\${release})\` : release }}
                                    <v-icon dark>arrow_drop_down</v-icon>
                                    </span>
                                    <v-list>
                                    <v-list-tile to="/" v-for="(release, i) in releases" v-if="i === 0" v-bind:key="release">
                                        <v-list-tile-title>{{ release }}</v-list-tile-title>
                                    </v-list-tile>
                                    <v-list-tile tag="a" v-else :href="\`/releases/\${release}\`">
                                        <v-list-tile-title>{{ release }}</v-list-tile-title>
                                    </v-list-tile>
                                    </v-list>
                                </v-menu>
                                <span v-else>
                                    Version: {{ release === releases[0] ? \`(\${release})\` : release }}
                                </span>
                                </v-list-tile-sub-title>
                            </v-list-tile-content>
                        </v-list-tile>
                    </v-list>
                </v-toolbar>
            <v-divider></v-divider>
            <v-list dense>
            <template v-for="(item_0,i) in items">
                <v-expansion-panel
                    v-if="item_0.items && item_0.depth > 2"
                    class="navGroups"
                >
                    <v-expansion-panel-content
                        hide-actions
                        v-for="(item_1,j) in item_0.items"
                        v-bind:ref="'group-'+i+'-'+j"
                        v-model="groups[i][j]"
                        :key="j"
                    >
                        <ul slot="header"
                            class="list--group__header"
                            v-bind:class="{ 'list--group__header--active': groups[i] === j}"
                            @click.stop="openGroup(i, j)">
                            <li>
                                <a class="list__tile list__tile--link" data-ripple="true" style="position: relative;">
                                    <v-list-tile-action v-if="item_1.action">
                                        <v-icon dark>{{ item_1.action }}</v-icon>
                                    </v-list-tile-action>
                                    <div class="list__tile__content">
                                        <div class="list__tile__title">{{ item_1.title }}</div>
                                    </div>
                                </a>
                            </li>
                        </ul>
                        <v-card 
                            @click.stop="click" 
                            class="navGroupItem"
                        >
                            <template v-for="(item_2,k) in item_1.items">
                                <v-list-group
                                    v-bind:ref="'group-'+i+'-'+j+'-'+k"
                                    v-if="item_2.items"
                                    v-bind:group="item_2.pathprefix"
                                >
                                    <v-list-tile
                                        ripple
                                        slot="item"
                                        @click.native="openGroupItem(i, j, k)"
                                    >
                                        <v-list-tile-action v-if="item_2.action">
                                            <v-icon dark>{{ item_2.action }}</v-icon>
                                        </v-list-tile-action>
                                        <v-list-tile-content>
                                            <v-list-tile-title>{{ item_2.title }}</v-list-tile-title>
                                        </v-list-tile-content>
                                        <v-list-tile-action>
                                            <v-icon dark>keyboard_arrow_down</v-icon>
                                        </v-list-tile-action>
                                    </v-list-tile>
                                    <v-list-tile
                                        v-for="item_3 in item_2.items" v-bind:key="item_3.title"
                                        v-bind="{ to: item_3.to, href: item_3.href }"
                                        @click.native="setIsSearching(false)"
                                        ripple
                                        v-bind:disabled="item_3.disabled"
                                        v-bind:target="item_3.target"
                                    >
                                        <v-list-tile-content>
                                            <v-list-tile-title>{{ item_3.title }}</v-list-tile-title>
                                        </v-list-tile-content>
                                    </v-list-tile>
                                </v-list-group>
                                <v-list-tile
                                    v-else
                                    v-bind="{ to: item_2.to, href: item_2.href }"
                                    @click.native="setIsSearching(false)"
                                    ripple
                                    v-bind:disabled="item_2.disabled"
                                    v-bind:target="item_2.target"
                                    rel="noopener"
                                >
                                    <v-list-tile-action v-if="item_2.action">
                                        <v-icon dark>{{ item_2.action }}</v-icon>
                                    </v-list-tile-action>
                                    <v-list-tile-content>
                                        <v-list-tile-title>{{ item_2.title }}</v-list-tile-title>
                                    </v-list-tile-content>
                                </v-list-tile>
                            </template>
                        </v-card>
                    </v-expansion-panel-content>
                </v-expansion-panel>
                <v-list-group v-else-if="item_0.items" v-bind:group="item_0.pathprefix">
                    <v-list-tile slot="item" ripple>
                        <v-list-tile-action>
                            <v-icon dark>{{ item_0.action }}</v-icon>
                        </v-list-tile-action>
                        <v-list-tile-content>
                            <v-list-tile-title>{{ item_0.title }}</v-list-tile-title>
                        </v-list-tile-content>
                        <v-list-tile-action>
                            <v-icon dark>keyboard_arrow_down</v-icon>
                        </v-list-tile-action>
                    </v-list-tile>
                    <v-list-tile
                        v-for="item_1 in item_0.items" v-bind:key="item_1.title"
                        v-bind="{ to: item_1.to, href: item_1.href }"
                        @click.native="setIsSearching(false)"
                        ripple
                        v-bind:disabled="item_1.disabled"
                        v-bind:target="item_1.target"
                    >
                        <v-list-tile-content>
                            <v-list-tile-title>{{ item_1.title }}</v-list-tile-title>
                        </v-list-tile-content>
                        <v-list-tile-action v-if="item_1.action">
                            <v-icon dark :class="[item_1.actionClass || 'success--text']">{{ item_1.action }}</v-icon>
                        </v-list-tile-action>
                    </v-list-tile>
                </v-list-group>
                <v-list-tile v-else-if="item_0.header" disabled>
                    <v-list-tile-content>
                        <v-list-tile-title>{{ item_0.header }}</v-list-tile-title>
                    </v-list-tile-content>
                </v-list-tile>
                <v-divider v-else-if="item_0.divider"></v-divider>
                <v-list-tile
                    v-bind="{ to: item_0.to, href: item_0.href }"
                    @click.native="setIsSearching(false)"
                    ripple
                    v-bind:disabled="item_0.disabled"
                    v-bind:target="item_0.target"
                    rel="noopener"
                    v-else
                >
                    <v-list-tile-action v-if="item_0.action">
                        <v-icon dark>{{ item_0.action }}</v-icon>
                    </v-list-tile-action>
                    <v-list-tile-content>
                        <v-list-tile-title>{{ item_0.title }}</v-list-tile-title>
                    </v-list-tile-content>
                </v-list-tile>
            </template>
            </v-list>
        </v-navigation-drawer>`;

        // insert it in the document
        const firstScriptElt = document.getElementsByTagName('script')[0];
        firstScriptElt.parentNode.insertBefore(scriptElt, firstScriptElt);

        function hue(color) {
            switch (color) {
                case 'purple':
                    return 420;
                case 'darken-3 pink':
                    return 480;
                case 'indigo':
                    return 370;
                case 'cyan':
                    return 337;
                case 'teal':
                    return 315;
                default:
                    return 0;
            }
        }

        Vue.component('docNav', {
            template: '#doc-nav',
            data: function () {
                return {
                    navIcon: config.navIcon,
                    navLogo: config.navLogo,
                    navTitle: config.navTitle,
                    release: config.release,
                    releases: config.releases,
                    items: navItems,
                    groups: [],
                    activeIndex: 0,
                    activeGroupIndex: 0
                };
            },
            created: function() {
                var first = false
                for (let i = 0; i < this.items.length; i++) {
                    if (this.items[i].depth > 2) {
                        var group = []
                        for(let j = 0; j < this.items[i].items.length ; j++) {
                            if (!first) {
                                group[j] = true
                                first = true
                            } else {
                                group[j] = false
                            }
                        }
                        this.groups.push(group)
                    } else {
                        this.groups.push(false)
                    }
                }
                this.$router.afterEach((to, from) => this.toggleGroupForRoute(to))
            },
            mounted: function () {
                this.toggleGroupForRoute(this.$route)
            },
            computed: {
                filter() {
                    return {
                        filter: `hue-rotate(${hue(this.$store.state.currentColor)}deg)`
                    };
                },
                isActive: {
                    get() {
                        return this.$store.state.sidebar;
                    },
                    set(val) {
                        this.$store.commit('vuetify/SIDEBAR', val);
                    }
                }
            },
            methods: {
                toggleGroupForRoute(route) {
                    const activeItem = this.items[this.activeIndex]
                    if (activeItem && activeItem.depth > 2) {
                        const activeGroup = activeItem.items[this.activeGroupIndex]
                        if (activeGroup.pathprefix && route.path.startsWith(activeGroup.pathprefix)) {
                            return
                        }
                        for (let i = 0; i < this.items.length; i++) {
                            const item_0 = this.items[i]
                            if (this.items[i].depth > 2) {
                                for (let j = 0; j < item_0.items.length; j++) {
                                    if (item_0 && item_0.items) {
                                        const item_1 = item_0.items[j]
                                        for (let k = 0; k < item_1.items.length; k++) {
                                            const item_2 = item_1.items[k]
                                            let match = false
                                            if (item_2.pathprefix && route.path.startsWith(item_2.pathprefix)) {
                                                this.openGroupItem(i, j, k)
                                                this.openGroup(i, j)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                },
                openGroup(i, j) {
                    if (!this.groups[i][j]) {
                        this.toggleGroup(this.activeIndex, this.activeGroupIndex)
                        this.toggleGroup(i, j)
                        this.activeIndex = i
                        this.activeGroupIndex = j
                    }
                    this.setIsSearching(false);
                },
                toggleGroup(i, j) {
                    const refName = `group-${i}-${j}`
                    const ref = this.$refs[refName]
                    if (ref && ref.length && ref.length == 1) {
                        ref[0].isActive = !ref[0].isActive
                    }
                },
                openGroupItem(i, j, k) {
                    const refName = `group-${i}-${j}-${k}`
                    const ref = this.$refs[refName]
                    if (ref && ref.length && ref.length == 1) {
                        ref[0].isActive = true
                    }
                    this.setIsSearching(false);
                },
                setIsSearching(val) {
                    this.$store.commit('sitegen/ISSEARCHING', val);
                }
            }
        });
    }
};
