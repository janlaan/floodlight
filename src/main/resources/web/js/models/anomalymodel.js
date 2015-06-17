/*
   Copyright 2012 IBM

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
*/

window.Anomaly = Backbone.Model.extend({

    defaults: {
        application: 0,
        API: 0,
        originalChance: 0,
        newChance: 0,
    },

    // initialize:function () {}

});

window.AnomalyCollection = Backbone.Collection.extend({

    model:Anomaly,
    
    fetch:function () {
        var self = this;
        //console.log("fetching host list")
        $.ajax({
            url:hackBase + "/wm/core/anomalies/json",
            dataType:"json",
            success:function (data) {
                var old_ids = self.pluck('id');
console.log(old_ids); console.log("-----data-----"); console.log(data);
                _.each(data, function(h) {
                    h.id = h.application + h.api;
                    old_ids = _.without(old_ids, h.id);
                        self.add(h, {silent: true});
                   // }
                });
                // old_ids now holds hosts that no longer exist; remove them
console.log(old_ids);
                _.each(old_ids, function(h) {
                    console.log("---removing anomaly " + h);
                    self.remove({id:h});
                });
                self.trigger('add'); // batch redraws
            }
        });

    },
    
});